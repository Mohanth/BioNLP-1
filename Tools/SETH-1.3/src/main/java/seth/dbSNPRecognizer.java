package seth;

import de.hu.berlin.wbi.objects.MutationMention;
import seth.ner.wrapper.Type;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detect dnSNP mentions in text (e.g., rs2345)
 * @author  Philippe Thomas
 */
public class dbSNPRecognizer {

    final private static String prefix="(^|[\\s\\(\\)\\[\\'\"/,\\-])"; //>
    final private static String midfix="(:?[ATGC]\\s?>\\s?[ATGC])?";
    final private static String suffix="(?=([\\.,\\s\\)\\(\\]\\'\":;\\-/]|$))";
    final private static Pattern dbSNP = Pattern.compile(prefix +"(rs[1-9][0-9]*)" +midfix +suffix);

    /**
     * FInd dbSNP mentions in a text
     * @param text Text to perform Named Entity Recognition
     * @return A list of dbSNP mentions found in the provided text
     */
    public List<MutationMention> extractMutations(String text) {
        List<MutationMention> result = new ArrayList<MutationMention>();

        Matcher matcher = dbSNP.matcher(text);
        while (matcher.find()){

            int begin = matcher.start(2);
            int end = matcher.group(3) == null ? matcher.end(2) : matcher.end(3);

            MutationMention mm = new MutationMention(begin, end, text.substring(begin, end), null, null, null, null, Type.DBSNP_MENTION, MutationMention.Tool.DBSNP);
            try{
                int rsId = Integer.parseInt(matcher.group(2).substring(2));
                mm.normalizeSNP(rsId);
            }catch(NumberFormatException nfe){}
            result.add(mm);
        }

        return result;
    }
}
