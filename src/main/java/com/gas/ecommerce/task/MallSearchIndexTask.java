package com.gas.ecommerce.task;

import com.gas.ecommerce.modules.mall.service.MallSearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.slf4j.LoggerFactory;

@Component
@ConditionalOnProperty(name="app.mall.elasticsearch.sync-enabled",havingValue="true")
public class MallSearchIndexTask {
    private final MallSearchService search;
    public MallSearchIndexTask(MallSearchService search) {this.search=search;}
    @Scheduled(initialDelayString="${app.mall.elasticsearch.sync-ms:300000}",fixedDelayString="${app.mall.elasticsearch.sync-ms:300000}")
    public void synchronize() {
        try {search.reindex();}
        catch(Exception e) {LoggerFactory.getLogger(getClass()).warn("Mall search synchronization failed: {}",e.toString());}
    }
}
