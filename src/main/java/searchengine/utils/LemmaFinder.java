package searchengine.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.lucene.morphology.LuceneMorphology;
import org.apache.lucene.morphology.english.EnglishLuceneMorphology;
import org.apache.lucene.morphology.russian.RussianLuceneMorphology;
import searchengine.services.IndexingServiceImpl;
import java.io.IOException;
import java.util.*;

public class LemmaFinder {
    private static final LuceneMorphology luceneMorphologyRu;
    private static final LuceneMorphology luceneMorphologyEn;
    private static final Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private static final String[] particlesNames = new String[]{"МЕЖД", "ПРЕДЛ", "СОЮЗ", "ЧАСТ",
            "ARTICLE", "CONJ", "PREP"};

    static {
        try {
            luceneMorphologyRu = new RussianLuceneMorphology();
            luceneMorphologyEn = new EnglishLuceneMorphology();
        } catch (IOException e) {
            logger.error("\u001B[31m*** MORPHOLOGY CONNECTION ERROR ***\u001B[0m");
            throw new RuntimeException(e);
        }
    }

    public static HashMap<String, Integer> collectLemmas(String text) {
        List<String> words = arrayContainsWords(text);
        HashMap<String, Integer> lemmas = new HashMap<>();
        for (String word : words) {
            if (word.isBlank()) continue;
            List<String> wordBaseForms = getMorphInfo(word);
            if (wordBaseForms.isEmpty() || anyWordBaseBelongToParticle(wordBaseForms)) {
                continue;
            }
            List<String> normalForms = getNormalForms(word);
            if (normalForms.isEmpty()) {
                continue;
            }
            String normalWord = normalForms.get(0);
            if (lemmas.containsKey(normalWord)) {
                lemmas.put(normalWord, lemmas.get(normalWord) + 1);
            } else {
                lemmas.put(normalWord, 1);
            }
        }
        return lemmas;
    }

    static boolean anyWordBaseBelongToParticle(List<String> wordBaseForms) {
        return wordBaseForms.stream().anyMatch(LemmaFinder::isParticle);
    }

    private static boolean isParticle(String wordBase) {
        for (String property : particlesNames) {
            if (wordBase.toUpperCase().contains(property)) {
                return true;
            }
        }
        return false;
    }

    static List<String> getMorphInfo(String word) {
        if (word.matches("[а-я]+")) {
            return luceneMorphologyRu.getMorphInfo(word);
        } else if (word.matches("[a-z]+")) {
            return luceneMorphologyEn.getMorphInfo(word);
        }
        return new ArrayList<>();
    }

    public static List<String> getNormalForms(String word) {
        if (word.matches("[а-я]+")) {
            return luceneMorphologyRu.getNormalForms(word);
        } else if (word.matches("[a-z]+")) {
            return luceneMorphologyEn.getNormalForms(word);
        }
        return new ArrayList<>();
    }

    static List<String> arrayContainsWords(String text) {
        return List.of(text.toLowerCase(Locale.ROOT)
                .replaceAll("([^а-яa-z\\s])", " ")
                .trim()
                .split("\\s+"));
    }

}