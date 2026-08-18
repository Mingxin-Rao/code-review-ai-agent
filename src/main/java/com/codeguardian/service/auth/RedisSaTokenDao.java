package com.codeguardian.service.auth;

import cn.dev33.satoken.dao.SaTokenDao;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ListOperations;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Sa-Token persistence layer implementation based on Spring Data Redis.
 *
 * <p>Responsibilities: read/write and TTL management for tokens, sessions and collections in Redis,
 * staying compatible with the Sa-Token 1.39+ {@code SaTokenDao} interface.
 * / Responsibility: manages read/write and expiration in Redis for data such as Token, Session and collections,
 * remaining compatible with the `SaTokenDao` interface of Sa-Token 1.39+.</p>
 */
public class RedisSaTokenDao implements SaTokenDao {

    private final StringRedisTemplate redis;
    private final RedisTemplate<String, Object> objectRedis;

    /**
     * Constructor
     *
     * @param redis string read/write template (for simple key-values)
     * @param objectRedis object read/write template (for objects such as Session)
     */
    public RedisSaTokenDao(StringRedisTemplate redis, RedisTemplate<String, Object> objectRedis) {
        this.redis = redis;
        this.objectRedis = objectRedis;
    }

    /**
     * Read a string value
     */
    @Override
    public String get(String key) {
        if (!StringUtils.hasText(key)) return null;
        ValueOperations<String, String> ops = redis.opsForValue();
        return ops.get(key);
    }

    /**
     * Write a string value and set its expiration (-1 means never expire)
     */
    @Override
    public void set(String key, String value, long timeout) {
        if (!StringUtils.hasText(key)) return;
        ValueOperations<String, String> ops = redis.opsForValue();
        if (timeout == -1) {
            ops.set(key, value);
        } else {
            ops.set(key, value, Duration.ofSeconds(timeout));
        }
    }

    /**
     * Overwrite a string value while keeping the original TTL
     */
    @Override
    public void update(String key, String value) {
        set(key, value, getTimeout(key));
    }

    /**
     * Delete a key
     */
    @Override
    public void delete(String key) {
        if (!StringUtils.hasText(key)) return;
        redis.delete(key);
    }

    /**
     * Get the remaining TTL of a key in seconds (-1 never expires, -2 does not exist)
     */
    @Override
    public long getTimeout(String key) {
        if (!StringUtils.hasText(key)) return -2;
        Long expire = redis.getExpire(key, TimeUnit.SECONDS);
        return expire == null ? -2 : expire;
    }

    /**
     * Update the expiration of a key (-1 sets it to never expire)
     */
    @Override
    public void updateTimeout(String key, long timeout) {
        if (!StringUtils.hasText(key)) return;
        if (timeout == -1) {
            redis.persist(key);
        } else {
            redis.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    /**
     * Read an object value
     */
    public Object getObject(String key) {
        if (!StringUtils.hasText(key)) return null;
        return objectRedis.opsForValue().get(key);
    }

    /**
     * Write an object and set its expiration
     */
    public void setObject(String key, Object object, long timeout) {
        if (!StringUtils.hasText(key)) return;
        if (timeout == -1) {
            objectRedis.opsForValue().set(key, object);
        } else {
            objectRedis.opsForValue().set(key, object, timeout, TimeUnit.SECONDS);
        }
    }

    /**
     * Overwrite an object while keeping the original TTL
     */
    public void updateObject(String key, Object object) {
        setObject(key, object, getObjectTimeout(key));
    }

    /**
     * Delete an object key
     */
    public void deleteObject(String key) {
        delete(key);
    }

    /**
     * Update the expiration of an object
     */
    @Override
    public void updateObjectTimeout(String key, long timeout) {
        updateTimeout(key, timeout);
    }

    /**
     * Get the remaining TTL of an object in seconds
     */
    @Override
    public long getObjectTimeout(String key) {
        return getTimeout(key);
    }

    /**
     * Read a list
     */
    public List<String> getList(String key) {
        ListOperations<String, String> ops = redis.opsForList();
        Long size = ops.size(key);
        if (size == null || size <= 0) return List.of();
        return ops.range(key, 0, -1);
    }

    /**
     * Write a list and set its expiration
     */
    public void setList(String key, List<String> list, long timeout) {
        delete(key);
        if (list == null || list.isEmpty()) return;
        ListOperations<String, String> ops = redis.opsForList();
        ops.rightPushAll(key, list);
        if (timeout != -1) {
            redis.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    /**
     * Overwrite a list while keeping the original TTL
     */
    public void updateList(String key, List<String> list) {
        long ttl = getTimeout(key);
        setList(key, list, ttl);
    }

    /**
     * Delete a list key
     */
    public void deleteList(String key) {
        delete(key);
    }

    /**
     * Read a Hash
     */
    public Map<String, String> getMap(String key) {
        HashOperations<String, String, String> ops = redis.opsForHash();
        Map<String, String> map = ops.entries(key);
        return map == null ? Map.of() : map;
    }

    /**
     * Write a Hash and set its expiration
     */
    public void setMap(String key, Map<String, String> map, long timeout) {
        delete(key);
        if (map == null || map.isEmpty()) return;
        HashOperations<String, String, String> ops = redis.opsForHash();
        ops.putAll(key, map);
        if (timeout != -1) {
            redis.expire(key, timeout, TimeUnit.SECONDS);
        }
    }

    /**
     * Overwrite a Hash while keeping the original TTL
     */
    public void updateMap(String key, Map<String, String> map) {
        long ttl = getTimeout(key);
        setMap(key, map, ttl);
    }

    /**
     * Delete a Hash key
     */
    public void deleteMap(String key) {
        delete(key);
    }

    /**
     * Search data (by prefix + keyword), with pagination and optional sorting
     */
    @Override
    public List<String> searchData(String prefix, String keyword, int start, int size, boolean sort) {
        var keys = redis.keys(prefix + "*" + (keyword == null ? "" : keyword) + "*");
        var list = keys == null ? List.<String>of() : List.copyOf(keys);
        if (sort && !list.isEmpty()) {
            list = list.stream().sorted().toList();
        }
        int from = Math.max(0, start);
        int to = size <= 0 ? list.size() : Math.min(list.size(), from + size);
        if (from >= list.size()) return List.of();
        return list.subList(from, to);
    }
}

