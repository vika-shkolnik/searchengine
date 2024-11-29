package searchengine.utils;

import lombok.Getter;
import lombok.Setter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import org.jsoup.Jsoup;
import org.tartarus.snowball.ext.RussianStemmer;
import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import static searchengine.utils.LemmaFinder.*;

@Getter
@Setter
public class SnippetSearch {
    private static Logger logger = LogManager.getLogger(SnippetSearch.class);
    private static final int lengthSnippet = 200;
    private static final LuceneMorphology luceneMorphologyRu;
    private static final LuceneMorphology luceneMorphologyEn;
    private static final RussianStemmer russianStemmer;
    private static StringBuilder builder;
    private static String snippet;

    static {
        try {
            luceneMorphologyRu = new RussianLuceneMorphology();
            luceneMorphologyEn = new EnglishLuceneMorphology();
            russianStemmer = new RussianStemmer();
        } catch (IOException e) {
            logger.error("\u001B[31m*** MORPHOLOGY CONNECTION ERROR ***\u001B[0m");
            throw new RuntimeException(e);
        }
    }

    public static String getSnippet(String content, String textQuery) {
        builder = new StringBuilder();
        snippet = "";
        textQuery = textQuery.trim();
        String textForSearchQuery = Jsoup.parse(content).getElementsContainingOwnText(textQuery).text(); //возвращает элементы, содержащие строку без учёта регистра
        Pattern pattern = Pattern.compile(textQuery, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
        Matcher matcher = pattern.matcher(textForSearchQuery);
        if (matcher.find()) {
            int beginIndex = textForSearchQuery.lastIndexOf('.', matcher.start()) + 1;
            int endIndex = Math.min((beginIndex + lengthSnippet), textForSearchQuery.length());
            snippet = textForSearchQuery.substring(beginIndex, endIndex).concat("...");
            matcher = pattern.matcher(snippet);
            builder = new StringBuilder(snippet);
            int countSymbolB = 0;
            while (matcher.find()) {
                int start = matcher.start() + countSymbolB;
                int end = matcher.end() + countSymbolB;
                builder.replace(start, end, bold(matcher.group()));
                countSymbolB = countSymbolB + 7;
            }
        } else {
            ArrayList<String> words = new ArrayList<>(arrayContainsWords(textQuery));
            String firstWord = words.get(0);
            if (!wordFormCheck(firstWord)) {
                String newFormWord = wordBasis(firstWord);
                String textForSearchWord = Jsoup.parse(content).getElementsContainingOwnText(firstWord).text();
                pattern = Pattern.compile(newFormWord, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                matcher = pattern.matcher(textForSearchWord);
                if (matcher.find()) {
                    int beginIndex = textForSearchWord.lastIndexOf('.', matcher.start()) + 1;
                    int endIndex = Math.min(beginIndex + lengthSnippet, textForSearchWord.length());
                    snippet = textForSearchWord.substring(beginIndex, endIndex).concat("...");
                    builder = new StringBuilder(snippet);
                    for (String word : words) {
                        if (!wordFormCheck(word) ) {
                            newFormWord = wordBasis(word);
                            pattern = Pattern.compile(newFormWord, Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
                            matcher = pattern.matcher(builder);
                            while (matcher.find()) {
                                int start = matcher.start();
                                int end = matcher.end();
                                builder.replace(start, end, bold(matcher.group()));
                            }
                        }
                    }
                }
            }
        }
        snippet = builder.toString();
        return snippet;
    }

    private static boolean wordFormCheck (String word) {
        List<String> wordBaseForms = getMorphInfo(word);
        return wordBaseForms.isEmpty() || anyWordBaseBelongToParticle(wordBaseForms);
    }

    private static String bold(String word) {
        return String.format("<b>%s</b>", word);
    }

    private static String wordBasis (String word) {
        russianStemmer.setCurrent(word);
        if (russianStemmer.stem()) {
            word = russianStemmer.getCurrent() + ".*?\\s";
        }
        return word;
    }

}

