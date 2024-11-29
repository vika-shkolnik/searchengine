package searchengine.utils;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import searchengine.model.Index;
import searchengine.model.Lemma;
import searchengine.model.Page;
import searchengine.model.Site;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import java.util.HashMap;

public record LemmaIndexer(Site modelSite, Page modelPage,
                           LemmaRepository repositoryLemma, IndexRepository repositoryIndex) {

    public void run() {
        String page = getAllText(modelPage.getContent());
        HashMap<String, Integer> lemmas = LemmaFinder.collectLemmas(page);
        lemmas.forEach(this::saveLemmaAndIndex);
    }

    public static String getTitleText (String content) {
        Document document = Jsoup.parse(content);
        return document.title();
    }

    public static String getAllText (String content) {
        Document document = Jsoup.parse(content);
        return document.text();
    }

    private void saveLemmaAndIndex(String lemma, Integer count) {
        synchronized (modelSite) {
            Lemma lemmaDB = repositoryLemma.findByLemmaAndSite(lemma, modelSite);
            if (lemmaDB == null) {
                Lemma lemmaNew = new Lemma();
                lemmaNew.setSite(modelSite);
                lemmaNew.setLemma(lemma);
                lemmaNew.setFrequency(1);
                lemmaDB = repositoryLemma.save(lemmaNew);
            }
            else {
                lemmaDB.setFrequency(lemmaDB.getFrequency() + 1);
                repositoryLemma.save(lemmaDB);
            }
            Index index = new Index();
            index.setPage(modelPage);
            index.setRang(count);
            index.setLemma(lemmaDB);
            repositoryIndex.save(index);
        }
    }

}