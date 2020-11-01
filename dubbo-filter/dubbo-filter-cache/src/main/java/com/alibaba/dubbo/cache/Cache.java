/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.dubbo.cache;

/**
 * Cache
 */

/**
 * Cache 是个缓存容器，内部可以管理缓存的键值
 *
 * 1）lru ：基于最近最少使用原则删除多余缓存，保持最热的数据被缓存。
 * 2）threadlocal ：当前线程缓存，比如一个页面渲染，用到很多 portal，每个 portal 都要去查用户信息，通过线程缓存，可以减少这种多余访问。
 * 3）jcache ：与 JSR107 集成，可以桥接各种缓存实现。
 */
public interface Cache {
    /**
     * 添加键值
     *
     * @param key   键
     * @param value 值
     */
    void put(Object key, Object value);

    /**
     * 获得值
     *
     * @param key 键
     * @return 值
     */
    Object get(Object key);

}
