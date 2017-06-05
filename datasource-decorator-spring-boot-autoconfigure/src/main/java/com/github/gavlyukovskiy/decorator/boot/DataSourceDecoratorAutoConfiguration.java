/*
 * Copyright 2017 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.github.gavlyukovskiy.decorator.boot;

import com.github.gavlyukovskiy.decorator.boot.metadata.DecoratedDataSourcePoolMetadataProvider;
import com.p6spy.engine.spy.P6DataSource;
import com.p6spy.engine.spy.P6SpyLoadableOptions;
import com.p6spy.engine.spy.P6SpyOptions;
import com.vladmihalcea.flexypool.FlexyPoolDataSource;
import net.ttddyy.dsproxy.listener.QueryExecutionListener;
import net.ttddyy.dsproxy.support.ProxyDataSource;
import net.ttddyy.dsproxy.support.ProxyDataSourceBuilder;
import net.ttddyy.dsproxy.transform.ParameterTransformer;
import net.ttddyy.dsproxy.transform.QueryTransformer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.metadata.DataSourcePoolMetadataProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;

import java.util.List;

/**
 * {@link EnableAutoConfiguration Auto-configuration} for proxying DataSource.
 *
 * @author Arthur Gavlyukovskiy
 */
@Configuration
@EnableConfigurationProperties(DataSourceDecoratorProperties.class)
@ConditionalOnProperty(prefix = "spring.datasource.decorator", name = "enabled", havingValue = "true", matchIfMissing = true)
@ConditionalOnBean(DataSource.class)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@Import({ DataSourceDecoratorAutoConfiguration.FlexyPool.class,
          DataSourceDecoratorAutoConfiguration.DataSourceProxy.class,
          DataSourceDecoratorAutoConfiguration.P6Spy.class })
public class DataSourceDecoratorAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSourceDecorator.class)
    public DataSourceDecoratorBeanPostProcessor dataSourceDecoratorBeanPostProcessor(
            ApplicationContext applicationContext,
            DataSourceDecoratorProperties properties) {
        return new DataSourceDecoratorBeanPostProcessor(applicationContext, properties);
    }

    @Bean
    public DataSourcePoolMetadataProvider proxyDataSourcePoolMetadataProvider() {
        return new DecoratedDataSourcePoolMetadataProvider();
    }

    @ConditionalOnClass(FlexyPoolDataSource.class)
    static class FlexyPool {

        @Bean
        public DataSourceDecorator flexyPoolDataSourceDecorator() {
            return (beanName, dataSource) -> {
                try {
                    return new FlexyPoolDataSource<>(dataSource);
                }
                catch (Exception e) {
                    return dataSource;
                }
            };
        }
    }

    /**
     * Configuration for datasource-proxy, allows to use define custom {@link QueryExecutionListener}s,
     * {@link ParameterTransformer} and {@link QueryTransformer}.
     */
    @ConditionalOnClass(ProxyDataSource.class)
    static class DataSourceProxy {

        @Autowired
        private DataSourceDecoratorProperties dataSourceDecoratorProperties;

        @Autowired(required = false)
        private List<QueryExecutionListener> listeners;

        @Autowired(required = false)
        private ParameterTransformer parameterTransformer;

        @Autowired(required = false)
        private QueryTransformer queryTransformer;

        @Bean
        @ConditionalOnMissingBean
        public ProxyDataSourceBuilder proxyDataSourceBuilder() {
            ProxyDataSourceBuilder proxyDataSourceBuilder = ProxyDataSourceBuilder.create();
            dataSourceDecoratorProperties.getDataSourceProxy().configure(proxyDataSourceBuilder);
            if (listeners != null) {
                listeners.forEach(proxyDataSourceBuilder::listener);
            }
            if (parameterTransformer != null) {
                proxyDataSourceBuilder.parameterTransformer(parameterTransformer);
            }
            if (queryTransformer != null) {
                proxyDataSourceBuilder.queryTransformer(queryTransformer);
            }
            return proxyDataSourceBuilder;
        }

        @Bean
        public DataSourceDecorator proxyDataSourceDecorator(ProxyDataSourceBuilder proxyDataSourceBuilder) {
            return (beanName, dataSource) -> proxyDataSourceBuilder.dataSource(dataSource).build();
        }
    }

    @ConditionalOnClass(P6DataSource.class)
    static class P6Spy {

        @Bean
        public DataSourceDecorator p6SpyDataSourceDecorator() {
            return (beanName, dataSource) -> {
                P6SpyLoadableOptions options = P6SpyOptions.getActiveInstance();
                options.setLogMessageFormat("com.p6spy.engine.spy.appender.MultiLineFormat");
                return new P6DataSource(dataSource);
            };
        }
    }
}