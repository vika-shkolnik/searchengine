package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.dto.search.DataSearchItem;
import searchengine.dto.search.SearchResponse;
import searchengine.model.*;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaFinder;
import searchengine.utils.LemmaIndexer;
import searchengine.utils.RelevancePage;
import searchengine.utils.SnippetSearch;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    @Autowired
    private SiteRepository repositorySite;
    @Autowired
    private PageRepository repositoryPage;
    @Autowired
    private LemmaRepository repositoryLemma;
    @Autowired
    private IndexRepository repositoryIndex;
    private final Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private static String lastQuery;
    private static List<DataSearchItem> data;


    @Override
    public SearchResponse getSearch(String query, String siteUrl, Integer offset, Integer limit) {
        if (query.isEmpty()) {
            logger.error("\u001B[31m*** AN EMPTY SEARCH QUERY IS SET ***\u001B[0m");
            return new SearchResponse("Задан пустой поисковый запрос");
        }
        offset = (offset == null) ? 0 : offset;
        limit = (limit == null) ? 20 : limit;
        if (query.equals(lastQuery)) {
            buildResponse(offset, limit);
        }
        Set<String> queryLemmas = LemmaFinder.collectLemmas(query).keySet();//Собираются леммы из запроса
        List<Index> foundIndexes;
        if (siteUrl == null) {
            List<Site> all = repositorySite.findAll();
            if (all.isEmpty()) {
                logger.error("\u001B[31m*** NOT A SINGLE SITE WAS FOUND ***\u001B[0m");
                return new SearchResponse("Не найдено ни одного сайта");
            }
            if (!isIndexed(all))
            {
                logger.error("\u001B[31m*** NO INDEXED SITES HAVE BEEN FOUND ***\u001B[0m");
                return new SearchResponse("Не найдено ни одного проиндексированного сайта");
            }
            foundIndexes = searchByAll(queryLemmas);
        }
        else {
            Site site = repositorySite.findSiteByUrl(siteUrl);
            if (site.getStatus() == Status.INDEXING)  {
                logger.error("\u001B[31m*** THE SELECTED SITE HAS NOT BEEN INDEXED YET ***\u001B[0m");
                return new SearchResponse("Выбранный сайт еще не проиндексирован");
            }
            if (site.getStatus() == Status.FAILED)  {
                logger.error("\u001B[31m*** INDEXING OF THE SITE FAILED. THE REQUEST HAS NOT BEEN PROCESSED ***\u001B[0m");
                return new SearchResponse("Индексация сайта завершилась ошибкой. Запрос не обработан");
            }
            foundIndexes = searchBySite(queryLemmas, site);
        }
        if (foundIndexes.isEmpty()) {
            logger.error("\u001B[31m*** NOTHING WAS FOUND ***\u001B[0m");
            return new SearchResponse("По сайту/сайтам ничего не найдено");
        }
        lastQuery = query;
        data = getDataList(foundIndexes);
        return buildResponse(offset, limit);
    }

    private List<Index> searchByAll(Set<String> queryLemmas) {
        List<Index> indexList = new ArrayList<>();
        List<Site> sites = repositorySite.findAll();
        for (Site site : sites) {
            if (site.getStatus() == Status.INDEXED) {
                indexList.addAll(searchBySite(queryLemmas, site));
            }
        }
        return indexList;
    }

    private List<Index> searchBySite(Set<String> queryLemmas, Site site) {
        List<Lemma> lemmas = repositoryLemma.selectLemmasBySite(queryLemmas, site);
        return lemmas.stream()
                .map(Lemma::getLemma)
                .collect(Collectors.toSet())
                .equals(queryLemmas)
                ? getIndexesCorrespondingTolLemmas(lemmas)
                : new ArrayList<>();
    }

    private List<Index> getIndexesCorrespondingTolLemmas(List<Lemma> lemmas) {
        List<Index> foundIndexes = new ArrayList<>();
        Set<Page> foundPages = new HashSet<>();
        if (lemmas.isEmpty()) return foundIndexes;
        lemmas.sort(Comparator.comparingInt(Lemma::getFrequency));
        foundIndexes = repositoryIndex.findByLemma(lemmas.get(0));
        foundIndexes.forEach(t -> foundPages.add(t.getPage()));
        for (Lemma lemma : lemmas.subList(1, lemmas.size())) {
            List<Index> indexesOfLemma = repositoryIndex.findByLemma(lemma);
            if (indexesOfLemma.isEmpty()) continue;
            List<Index> newIndexesOfLemma = new ArrayList<>();
            for (Index index : indexesOfLemma) {
                if (foundPages.contains(index.getPage())) {
                    newIndexesOfLemma.add(index);
                }
            }
            foundPages.clear();
            newIndexesOfLemma.forEach(t -> foundPages.add(t.getPage()));
            foundIndexes.addAll(newIndexesOfLemma);
        }
        foundIndexes.removeIf(index -> !foundPages.contains(index.getPage()));
        return foundIndexes;
    }
    private List<DataSearchItem> getDataList(List<Index>indexes) {
        List<RelevancePage> relevancePages = getRelevantList(indexes);
        List<DataSearchItem> result = new ArrayList<>();
        for (RelevancePage page : relevancePages) {
            DataSearchItem item = new DataSearchItem();
            String snippet = SnippetSearch.getSnippet(page.getPage().getContent(), lastQuery);
            if (snippet.isEmpty() || snippet.equals("...")) continue;
            item.setSite(page.getPage().getSite().getUrl());
            item.setSiteName(page.getPage().getSite().getName());
            item.setUri(page.getPage().getPath());
            String title = LemmaIndexer.getTitleText(page.getPage().getContent());
            if (title.length() > 50) {
                title = title.substring(0,50).concat("...");
            }
            item.setTitle(title);
            item.setRelevance(page.getRelevance());
            item.setSnippet(snippet);
            result.add(item);
        }
        return result;
    }

    private List<RelevancePage> getRelevantList(List<Index> indexes) {
        List<RelevancePage> pageSet = new ArrayList<>();
        for (Index index : indexes) {
            RelevancePage existingPage = pageSet.stream().filter(t -> t.getPage().equals(index.getPage())).findFirst().orElse(null);
            if (existingPage != null) {
                existingPage.putRankWord(index.getLemma().getLemma(), index.getRang());
                continue;
            }
            RelevancePage page = new RelevancePage(index.getPage());
            page.putRankWord(index.getLemma().getLemma(), index.getRang());
            pageSet.add(page);
        }
        float maxRelevance = 0.0f;
        for (RelevancePage page : pageSet) {
            float absRelevance = page.getAbsRelevance();
            if (absRelevance > maxRelevance) {
                maxRelevance = absRelevance;
            }
        }
        for (RelevancePage page : pageSet) {
            page.setRelevance(page.getAbsRelevance() / maxRelevance);
        }
        pageSet.sort(Comparator.comparingDouble(RelevancePage::getRelevance).reversed());
        return pageSet;
    }

    private SearchResponse buildResponse(Integer offset, Integer limit) {
        if (offset + limit >= data.size()) {
            limit = data.size() - offset;
        }
        return new SearchResponse(data.size(), data.subList(offset, offset + limit));
    }

    private boolean isIndexed (List<Site> all) {
        for (Site site : all) {
            if (site.getStatus() == Status.INDEXED){
                return true;
            }
        }
        return false;
    }

}
