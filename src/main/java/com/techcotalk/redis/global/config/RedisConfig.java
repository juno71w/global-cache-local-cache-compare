package com.techcotalk.redis.global.config;

import com.techcotalk.redis.service.GlobalCacheGameService;
import com.techcotalk.redis.service.LocalCacheGameService;
import com.techcotalk.redis.service.RdbmsGameService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.listener.adapter.MessageListenerAdapter;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory connectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(connectionFactory);
        template.setKeySerializer(new StringRedisSerializer());
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        template.setHashKeySerializer(new StringRedisSerializer());
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());
        return template;
    }

    @Bean
    public ChannelTopic rdbmsTopic() {
        return new ChannelTopic("rdbms-events");
    }

    @Bean
    public ChannelTopic globalCacheTopic() {
        return new ChannelTopic("global-cache-events");
    }

    @Bean
    public ChannelTopic localCacheTopic() {
        return new ChannelTopic("local-cache-events");
    }

    @Bean
    public RedisMessageListenerContainer redisMessageListenerContainer(
            RedisConnectionFactory connectionFactory,
            RdbmsGameService rdbmsGameService,
            GlobalCacheGameService globalCacheGameService,
            LocalCacheGameService localCacheGameService,
            @Qualifier("rdbmsTopic") ChannelTopic rdbmsTopic,
            @Qualifier("globalCacheTopic") ChannelTopic globalCacheTopic,
            @Qualifier("localCacheTopic") ChannelTopic localCacheTopic) {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);

        // Register Listeners
        container.addMessageListener(new MessageListenerAdapter(rdbmsGameService), rdbmsTopic);
        container.addMessageListener(new MessageListenerAdapter(globalCacheGameService), globalCacheTopic);
        container.addMessageListener(new MessageListenerAdapter(localCacheGameService), localCacheTopic);

        return container;
    }
}
