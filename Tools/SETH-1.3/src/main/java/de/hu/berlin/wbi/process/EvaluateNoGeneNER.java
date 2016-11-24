package de.hu.berlin.wbi.process;
/**
Copyright 2010, 2011 Philippe Thomas
This file is part of snp-normalizer.

snp-normalizer is free software: you can redistribute it and/or modify
it under the terms of the GNU General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.

snp-normalizer is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU General Public License for more details.

You should have received a copy of the GNU General Public License
along with snp-normalizer.  If not, see <http://www.gnu.org/licenses/>.
*/

import de.hu.berlin.wbi.objects.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.text.DecimalFormat;
import java.util.*;

/**
 * Class is used to evaluate snp-normalizer; 
 * Corpus is not provided due to licence restrictions but a link is provided in the paper..
 * @author Philippe Thomas
 */
public class EvaluateNoGeneNER {
	
	public static void main(String[] args) throws IOException, SQLException {
		
		if(args.length != 2){
			printUsage();
			System.exit(1);
		}
		
		String propertyFile = args[0];
		String mutationsFile=args[1]; 
					
		 Map<Integer,List<MutationValidation>>  mutations = null;	//Gold standard-mutations
		
		Properties property = new Properties();
		try {
			property.loadFromXML(new FileInputStream(new File(propertyFile)));
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} 
		
		DatabaseConnection mysql = new DatabaseConnection(property);
		mysql.connect();
		try {
			mutations = MutationValidation.readMutationsValidation(mutationsFile); //Read the annotated mutations from a file
		} catch (Exception e) {
			e.printStackTrace();
			System.exit(1);
		} 
		
		try {
			dbSNP.init(mysql, property.getProperty("database.PSM"), property.getProperty("database.hgvs_view"));
			Gene.init(mysql, property.getProperty("database.geneTable"), property.getProperty("database.gene2pubmed"));
			UniprotFeature.init(mysql, property.getProperty("database.uniprot"));
		} catch (SQLException e) {
			throw new RuntimeException(e);
		}



		//Perform the actual validation..
		int tp=0; int fp=0; int fn=0;
		
		for(int pmid : mutations.keySet()){


			for(MutationValidation mutation:mutations.get(pmid)){ 	//Iterate over mutations loaded from "mutationsFile"

                //Retrieves gene associated with a dbSNP entry
                //This allows us to 1.) ignore the impact of gene-NER and 2.) indirectly checks if we know the dbSNP ID at all
                mysql.query("SELECT DISTINCT locus_id FROM PSM WHERE snp_id = " +mutation.getDbSNP()  +" UNION SELECT  locus_id FROM hgvs WHERE snp_id = " +mutation.getDbSNP());
                ResultSet rs = mysql.getRs();
                Set<Gene> genes = new HashSet<Gene>();
                while(rs.next()){
                    int geneId = rs.getInt("locus_id");
                    genes.add(new Gene(pmid, geneId, 9606));
                }
                rs.close();

                if(genes.size() ==0 ){
                    System.err.println("!No associated gene found for mutation " +mutation.getDbSNP());
                }



				List<dbSNPNormalized> normalized  = new ArrayList<dbSNPNormalized>();		
				for(Gene gene:genes){
					if(gene.getSpecies() != 9606)		//Normalisation currently only for human genes
						continue;
					
					final List<dbSNP> potentialSNPs = dbSNP.getSNP(gene.getGeneID());
					final List<UniprotFeature> features = UniprotFeature.getFeatures(gene.getGeneID());
                    mutation.normalizeSNP(potentialSNPs, features, true); //Normalization happens here
				}
                if(mutation.getNormalized() != null)
                    normalized.addAll(mutation.getBestNormalized());

				Set<Integer> ids = new HashSet<Integer>(); //Contains the rsID's found by the normalisation procedure 
				for(dbSNPNormalized dbSNPNorm:normalized){
					ids.add(dbSNPNorm.getRsID());
				}
	 
				if(ids.contains(mutation.getDbSNP())){		//Check if found rsID's  is correct
					tp++;			
					ids.remove(mutation.getDbSNP());
				}			
				else{										//Otherwise we have a false negative
					fn++;
				}
								
				fp+=ids.size();			//All remaining ids are false positives	
				//				for(int id:ids)
				//					System.err.println("FP" +"\t" +pmid +"\t" +id +" for " +mutation.getDbSNP() +" " +mutation.toString());

				if(ids.size() != 0){

				    StringBuilder sb = new StringBuilder("FP" +"\t" +pmid +"\t" +mutation.toString() +"\t" +mutation.getDbSNP() +"\t found: ");
				    for(dbSNPNormalized dbSNPNorm:normalized){
					sb.append("'" +dbSNPNorm.getRsID() +"'/" + dbSNPNorm.getConfidence() +"/" +dbSNPNorm.getMatchType() +" ");
				    }
				    System.out.println(sb);
				}
			}
		}
		
		
        double recall = (double) tp/(tp+fn);
        double precision = (double) tp/(tp+fp);
        double f1 = 2*(precision*recall)/(precision+recall);

		DecimalFormat df = new DecimalFormat( "0.00" );
		System.err.println("TP " +tp);
		System.err.println("FP " +fp);
		System.err.println("FN " +fn);
        System.err.println("Precision " +df.format(precision));
		System.err.println("Recall " +df.format(recall));
        System.err.println("F1 " +df.format(f1));

	}

	private static void printUsage() {
		
		System.err.println("Evaluates normalisation results using the manually annotated corpus");
		System.err.println("Usage: java de.hu.berlin.wbi.process.Evaluate property-file corpus-file\n");
		System.err.println("Arguments:");
		System.err.println("\t property-file\t-> File containing the property settings to access the database");
		System.err.println("\t corpus-file\t-> File containing the annotated corpus to evaluate the normalisation process");
		
	}
}
