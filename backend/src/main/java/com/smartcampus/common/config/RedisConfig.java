package com.smartcampus.common.config;

import io.netty.resolver.DefaultAddressResolverGroup;
import org.springframework.boot.data.redis.autoconfigure.ClientResourcesBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

/**
 * Redis configuration providing StringRedisTemplate for distributed rate limiting and caching.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Resolve the Redis host with the JVM/operating-system resolver instead of Netty's own DNS client.
     * Netty reads the nameservers once at startup and keeps querying them; when the network's DNS changes
     * (VPNs such as Cloudflare WARP, Wi-Fi switches) every reconnect then times out, Redis stays "unavailable"
     * and login fails closed with 429. The OS resolver always follows the current network.
     */
    @Bean
    public ClientResourcesBuilderCustomizer systemDnsResolver() {
        return builder -> builder.addressResolverGroup(DefaultAddressResolverGroup.INSTANCE);
    }
}
