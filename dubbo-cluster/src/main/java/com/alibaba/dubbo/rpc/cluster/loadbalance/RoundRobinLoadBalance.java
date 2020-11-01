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
package com.alibaba.dubbo.rpc.cluster.loadbalance;

import com.alibaba.dubbo.common.URL;
import com.alibaba.dubbo.common.utils.AtomicPositiveInteger;
import com.alibaba.dubbo.rpc.Invocation;
import com.alibaba.dubbo.rpc.Invoker;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Round robin load balance.
 *
 */

/**
 * 实现 AbstractLoadBalance 抽象类，轮循，按公约后的权重设置轮循比率
 */
public class RoundRobinLoadBalance extends AbstractLoadBalance {

    public static final String NAME = "roundrobin";

    /**
     *  服务方法与计数器的映射
     */
    private final ConcurrentMap<String, AtomicPositiveInteger> sequences = new ConcurrentHashMap<String, AtomicPositiveInteger>();

    /**
     * 假定有3台权重都一样的dubbo provider:
     *
     * 10.0.0.1:20884, weight=100
     * 10.0.0.1:20886, weight=100
     * 10.0.0.1:20888, weight=100
     * 轮询算法的实现：
     * 其调用方法某个方法(key)的 sequence 从 0 开始：
     *
     * sequence=0时，选择invokers.get(0%3)=10.0.0.1:20884
     * sequence=1时，选择invokers.get(1%3)=10.0.0.1:20886
     * sequence=2时，选择invokers.get(2%3)=10.0.0.1:20888
     * sequence=3时，选择invokers.get(3%3)=10.0.0.1:20884
     * sequence=4时，选择invokers.get(4%3)=10.0.0.1:20886
     * sequence=5时，选择invokers.get(5%3)=10.0.0.1:20888
     *
     *
     * 如果有3台权重不一样的dubbo provider：
     *
     * 10.0.0.1:20884, weight=50
     * 10.0.0.1:20886, weight=100
     * 10.0.0.1:20888, weight=150
     *
     * 调试过很多次，这种情况下有问题；留一个TODO；
     */
    protected <T> Invoker<T> doSelect(List<Invoker<T>> invokers, URL url, Invocation invocation) {
        String key = invokers.get(0).getUrl().getServiceKey() + "." + invocation.getMethodName();
        int length = invokers.size(); // Number of invokers
        int maxWeight = 0; // The maximum weight
        int minWeight = Integer.MAX_VALUE; // The minimum weight
        final LinkedHashMap<Invoker<T>, IntegerWrapper> invokerToWeightMap = new LinkedHashMap<Invoker<T>, IntegerWrapper>();
        int weightSum = 0;
        // 计算最小、最大权重，总的权重和
        for (int i = 0; i < length; i++) {
            int weight = getWeight(invokers.get(i), invocation);
            maxWeight = Math.max(maxWeight, weight); // Choose the maximum weight
            minWeight = Math.min(minWeight, weight); // Choose the minimum weight
            if (weight > 0) {
                invokerToWeightMap.put(invokers.get(i), new IntegerWrapper(weight));
                weightSum += weight;
            }
        }
        // 获得 AtomicPositiveInteger 对象
        AtomicPositiveInteger sequence = sequences.get(key);
        if (sequence == null) {
            sequences.putIfAbsent(key, new AtomicPositiveInteger());
            sequence = sequences.get(key);
        }
        // 获得当前顺序号，并递增 + 1
        int currentSequence = sequence.getAndIncrement();
        if (maxWeight > 0 && minWeight < maxWeight) {
            int mod = currentSequence % weightSum;
            for (int i = 0; i < maxWeight; i++) {
                for (Map.Entry<Invoker<T>, IntegerWrapper> each : invokerToWeightMap.entrySet()) {
                    final Invoker<T> k = each.getKey();
                    final IntegerWrapper v = each.getValue();
                    if (mod == 0 && v.getValue() > 0) {
                        return k;
                    }
                    // 若 Invoker 还有权重值，扣除它( value )和剩余权重( mod )。
                    if (v.getValue() > 0) {
                        v.decrement();
                        mod--;
                    }
                }
            }
        }
        // 权重相等，平均顺序获得
        // Round robin
        return invokers.get(currentSequence % length);
    }

    private static final class IntegerWrapper {
        private int value;// 权重值

        public IntegerWrapper(int value) {
            this.value = value;
        }

        public int getValue() {
            return value;
        }

        public void setValue(int value) {
            this.value = value;
        }

        public void decrement() {// 扣除一
            this.value--;
        }
    }

}