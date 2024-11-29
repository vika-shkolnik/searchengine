package searchengine.services;

import lombok.RequiredArgsConstructor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.jsoup.Connection;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import searchengine.model.*;
import searchengine.config.SitesList;
import searchengine.config.UserAgents;
import searchengine.dto.index.IndexingResponse;
import searchengine.repositories.IndexRepository;
import searchengine.repositories.LemmaRepository;
import searchengine.repositories.PageRepository;
import searchengine.repositories.SiteRepository;
import searchengine.utils.LemmaIndexer;
import searchengine.utils.LinkParser;
import searchengine.utils.SiteIndexer;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ForkJoinPool;

@Service
@RequiredArgsConstructor
public class IndexingServiceImpl implements IndexingService {
    private final Logger logger = LogManager.getLogger(IndexingServiceImpl.class);
    private final SitesList sites;
    private final UserAgents agentCfg;
    @Autowired
    private SiteRepository repositorySite;
    @Autowired
    private PageRepository repositoryPage;
    @Autowired
    private LemmaRepository repositoryLemma;
    @Autowired
    private IndexRepository repositoryIndex;

    @Override
    public IndexingResponse startIndexing() {
        if (isIndexing()) {
            logger.error("\u001B[32m*** INDEXING IS ALREADY RUNNING ***\u001B[0m");
            return new IndexingResponse("Индексация уже запущена");
        }
        logger.info("\u001B[32m*** START INDEXING ***\u001B[0m");
        repositoryIndex.deleteAll();
        repositoryLemma.deleteAll();
        repositoryPage.deleteAll();
        repositorySite.deleteAll();
        for (searchengine.config.Site site : sites.getSites()) {
            new Thread(() -> {
                Site modelSite = new Site(Status.INDEXING, new Date(), site.getUrl(), site.getName());
                repositorySite.save(modelSite);
                new ForkJoinPool().invoke(new SiteIndexer(modelSite, repositoryPage, repositorySite,
                        repositoryLemma, repositoryIndex, agentCfg));
                if ((endIndexing(modelSite.getUrl()))) { //После завершения всех задач ForkJoinPool одного сайта проверяется
                    modelSite.setStatusTime(new Date());
                    modelSite.setStatus(Status.INDEXED);
                    repositorySite.save(modelSite);
                    logger.info("\u001B[32m*** SITE {} IS INDEXED ***\u001B[0m", site.getUrl());
                }
            }).start();
        }
        return new IndexingResponse();
    }

    private boolean isIndexing() {
        List<Site> all = repositorySite.findAll();
         for (Site site : all) {
            if (site.getStatus() == Status.INDEXING){
                return true;
            }
        }
        return false;
    }

    private boolean endIndexing(String url) {
        Site site = repositorySite.findSiteByUrl(url);
        return site.getStatus() == Status.INDEXING;
    }

    @Override
    public IndexingResponse stopIndexing() {
        if (!isIndexing()) {
            logger.error("\u001B[31m*** INDEXING IS NOT RUNNING ***\u001B[0m");
            return new IndexingResponse("Индексация не запущена");
        }
        SiteIndexer.stopIndexing();
        List<Site> all = repositorySite.findAll();
        for (Site site : all) {
            if (site.getStatus() == Status.INDEXING) {
                site.setStatus(Status.FAILED);
                site.setStatusTime(new Date());
                site.setLastError("Индексация остановлена пользователем");
                repositorySite.save(site);
                logger.error("\u001B[31m*** INDEXING FOR SITE {} HAS BEEN STOPPED BY THE USER ***\u001B[0m", site.getUrl());
            }
        }
        return new IndexingResponse();
    }

    @Override
    public synchronized IndexingResponse indexPage(String url) {
        if (url.trim().isEmpty()) {
            logger.error("\u001B[31m*** THE PAGE {} IS NOT SPECIFIED ***\u001B[0m", url);
            return new IndexingResponse("Страница не указана");
        }
        url = endUrl(url).trim();
        searchengine.config.Site siteCfg = findSiteCfgByUrl(url);
        if (siteCfg == null) {
            logger.error("\u001B[31m*** THIS PAGE {} IS LOCATED OUTSIDE THE SITES SPECIFIED IN THE CONFIGURATION FILE ***\u001B[0m",url);
            return new IndexingResponse("Данная страница находится за пределами сайтов, указанных в конфигурационном файле");
        }
        Site modelSite = repositorySite.findSiteByUrl(siteCfg.getUrl());
        if (modelSite == null) {
            modelSite = new Site(null, new Date(), siteCfg.getUrl(), siteCfg.getName());
            repositorySite.save(modelSite);
        }
        String path = SiteIndexer.getPath(url);
        path = path.isEmpty() ? "/" : path;
        deletePage(path, modelSite);
        return indexingAndSavePage(url, modelSite, path);
    }

    private String endUrl (String url) {
        return url.replace("www.", "");
    }

    private searchengine.config.Site findSiteCfgByUrl(String url) {
        return sites.getSites().stream()
                .filter(site -> url.contains(endUrl(site.getUrl())))
                .findFirst()
                .orElse(null);
    }

    private IndexingResponse indexingAndSavePage(String url, Site modelSite, String path) {
        Connection connection = LinkParser.getConnection(url, agentCfg);
        int statusCode = LinkParser.getStatusCode(connection);
        if (statusCode >= 400 && statusCode <= 599) {
            logger.error("\u001B[31m*** THIS PAGE IS UNAVAILABLE - {}, STATUSCODE {} ***\u001B[31m", url, statusCode);
            return new IndexingResponse("Страница недоступна. Код ответа: " + statusCode);
        }
        String content = LinkParser.getContent(connection);
        Page newPage = new Page(modelSite, path, statusCode, content);
        repositoryPage.save(newPage);
        new LemmaIndexer(modelSite, newPage, repositoryLemma, repositoryIndex).run();
        logger.info("\u001B[32m*** PAGE {} IS INDEXED ***\u001B[32m", url);
        modelSite.setStatus(repositorySite.findStatusByUrl(modelSite.getUrl()).getStatus());
        if (modelSite.getStatus() != Status.FAILED) {
            modelSite.setLastError("");
        }
        modelSite.setStatusTime(new Date());
        repositorySite.save(modelSite);
        return new IndexingResponse();
    }

    private void deletePage(String path, Site modelSite) {
        Page oldPage = repositoryPage.findPageByPathAndSite(path, modelSite);
        if (oldPage != null) {
            List<Index> entities = repositoryIndex.findByPageIn(oldPage);
            entities.forEach(entity -> {
                Lemma lemma = entity.getLemma();
                lemma.setFrequency(lemma.getFrequency() - 1);
                repositoryLemma.save(lemma);
            });
            repositoryIndex.deleteAll(entities);
            repositoryPage.delete(oldPage);
        }
    }

}

