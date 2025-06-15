/*
 * Copyright 2014 The Netty Project
 *
 * The Netty Project licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package io.netty.util.concurrent;

import io.netty.util.internal.InternalThreadLocalMap;
import io.netty.util.internal.PlatformDependent;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import static io.netty.util.internal.InternalThreadLocalMap.UNSET;
import static io.netty.util.internal.InternalThreadLocalMap.VARIABLES_TO_REMOVE_INDEX;

/**
 * A special variant of {@link ThreadLocal} that yields higher access performance when accessed from a
 * {@link FastThreadLocalThread}.
 * <p>
 * Internally, a {@link FastThreadLocal} uses a constant index in an array, instead of using hash code and hash table,
 * to look for a variable.  Although seemingly very subtle, it yields slight performance advantage over using a hash
 * table, and it is useful when accessed frequently.
 * </p><p>
 * To take advantage of this thread-local variable, your thread must be a {@link FastThreadLocalThread} or its subtype.
 * By default, all threads created by {@link DefaultThreadFactory} are {@link FastThreadLocalThread} due to this reason.
 * </p><p>
 * Note that the fast path is only possible on threads that extend {@link FastThreadLocalThread}, because it requires
 * a special field to store the necessary state.  An access by any other kind of thread falls back to a regular
 * {@link ThreadLocal}.
 * </p>
 * <pre>
 * 针对ThreadLocal的优化，使用InternalThreadLocalMap存储数据，所以核心优化实际上在InternalThreadLocalMap和ThreadLocalMap的差异
 * 原生ThreadLocal原理：
 * 1. 在Thread中携带一个Thread$ThreadLocalMap的实例，以ThreadLocal为键，value为值，键为弱引用，帮助回收，但是依然依赖remove方法
 * 2. ThreadLocalMap本身是一个使用线性探测法的哈希表，底层采用数组存储数据，可以理解成一个键是ThreadLocal的Map
 *    线性探测的意思是：如果当前索引值已经有数据存放，就移动到下一个索引，直到有空的位置用来存放。HashMap的方式叫拉链法
 *    之所以netty需要优化是因为在数据密集时，很容易出现hash冲突，这就会导致需要O(n)的时间寻找条目(逐个，直到找到或找到null)
 * 优化核心是{@link FastThreadLocalThread}和{@link InternalThreadLocalMap}类，包括：
 * 1. 将ThreadLocalMap的线性探测改为索引寻值
 * 2. 解决了Threadlocal生命周期管理复杂的问题
 *
 * 总结：
 *  通过FastThreadLocalThread维护InternalThreadLocalMap实例，让每一个FastThreadLocal维护一个从InternalThreadLocalMap获取的的index，
 *  表示自己的数据存在其内部的数组的索引，以实现O(1)级别的get。
 *  并且通过在InterThreadLocalMap中的数组索引为0的位置维护Set&lt;FastThreadLocal&gt;，将removeAll这种批量清理操作的开销降到O(1)，
 *  尽管这种方式会将remove单个的成本略微提高，属于是将removeAll的成本分摊到remove操作了。
 *  </pre>
 *  <pre>
 *  谈一谈这种设计的一个重大缺陷：
 *      FastThreadLocal.index是全局递增，即使当前线程只使用了一个变量，如果这个变量的 index = 8192，它仍然要分配一个8192大小的数组；
 *      本质上是追求极端性能的代价，只能说索引寻值的性能>>>线性探测
 *      也正是因为这种数组膨胀的可能性，也就更依赖于魔法槽位0来优化removeAll的清理操作
 *  </pre>
 *  相关类见：{@link FastThreadLocalThread}、{@link InternalThreadLocalMap}
 * @param <V> the type of the thread-local variable
 */
public class FastThreadLocal<V> {

    /**
     * Removes all {@link FastThreadLocal} variables bound to the current thread.  This operation is useful when you
     * are in a container environment, and you don't want to leave the thread local variables in the threads you do not
     * manage.
     */
    public static void removeAll() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return;
        }

        try {
            Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);
            if (v != null && v != InternalThreadLocalMap.UNSET) {
                @SuppressWarnings("unchecked")
                Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
                FastThreadLocal<?>[] variablesToRemoveArray =
                        variablesToRemove.toArray(new FastThreadLocal[0]);
                for (FastThreadLocal<?> tlv: variablesToRemoveArray) {
                    tlv.remove(threadLocalMap);
                }
            }
        } finally {
            InternalThreadLocalMap.remove();
        }
    }

    /**
     * Returns the number of thread local variables bound to the current thread.
     */
    public static int size() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap == null) {
            return 0;
        } else {
            return threadLocalMap.size();
        }
    }

    /**
     * Destroys the data structure that keeps all {@link FastThreadLocal} variables accessed from
     * non-{@link FastThreadLocalThread}s.  This operation is useful when you are in a container environment, and you
     * do not want to leave the thread local variables in the threads you do not manage.  Call this method when your
     * application is being unloaded from the container.
     */
    public static void destroy() {
        InternalThreadLocalMap.destroy();
    }

    @SuppressWarnings("unchecked")
    private static void addToVariablesToRemove(InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {
        // 获取下标为0的元素
        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);
        Set<FastThreadLocal<?>> variablesToRemove;
        if (v == InternalThreadLocalMap.UNSET || v == null) {
            // 创建FastThreadLocal类型的集合
            variablesToRemove = Collections.newSetFromMap(new IdentityHashMap<FastThreadLocal<?>, Boolean>());
            // 将set集合填充到0索引
            threadLocalMap.setIndexedVariable(VARIABLES_TO_REMOVE_INDEX, variablesToRemove);
        } else {
            // 如果不是默认值/null，说明已经创建过，直接强转成Set
            variablesToRemove = (Set<FastThreadLocal<?>>) v;
        }
        // 加到集合里
        variablesToRemove.add(variable);
    }

    private static void removeFromVariablesToRemove(
            InternalThreadLocalMap threadLocalMap, FastThreadLocal<?> variable) {

        Object v = threadLocalMap.indexedVariable(VARIABLES_TO_REMOVE_INDEX);

        if (v == InternalThreadLocalMap.UNSET || v == null) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<FastThreadLocal<?>> variablesToRemove = (Set<FastThreadLocal<?>>) v;
        variablesToRemove.remove(variable);
    }

    // 每一个FastThreadLocal对象都有一个从InternalThreadLocalMap生成的index值，表示自己在这个Map中存数据的索引
    private final int index;

    public FastThreadLocal() {
        // 构造时获取一个索引，代表下一个可以操作的索引位置
        index = InternalThreadLocalMap.nextVariableIndex();
    }

    /**
     * Returns the current value for the current thread
     */
    @SuppressWarnings("unchecked")
    public final V get() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
        Object v = threadLocalMap.indexedVariable(index); // 直接从索引拿值O(1)
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }
        // 如果是默认对象，需要初始化
        return initialize(threadLocalMap);
    }

    /**
     * Returns the current value for the current thread if it exists, {@code null} otherwise.
     */
    @SuppressWarnings("unchecked")
    public final V getIfExists() {
        InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.getIfSet();
        if (threadLocalMap != null) {
            Object v = threadLocalMap.indexedVariable(index);
            if (v != InternalThreadLocalMap.UNSET) {
                return (V) v;
            }
        }
        return null;
    }

    /**
     * Returns the current value for the specified thread local map.
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final V get(InternalThreadLocalMap threadLocalMap) {
        Object v = threadLocalMap.indexedVariable(index);
        if (v != InternalThreadLocalMap.UNSET) {
            return (V) v;
        }

        return initialize(threadLocalMap);
    }

    private V initialize(InternalThreadLocalMap threadLocalMap) {
        V v = null;
        try {
            v = initialValue();
            if (v == InternalThreadLocalMap.UNSET) {
                throw new IllegalArgumentException("InternalThreadLocalMap.UNSET can not be initial value.");
            }
        } catch (Exception e) {
            PlatformDependent.throwException(e);
        }

        threadLocalMap.setIndexedVariable(index, v);
        addToVariablesToRemove(threadLocalMap, this);
        return v;
    }

    /**
     * Set the value for the current thread.
     */
    public final void set(V value) {
        getAndSet(value);
    }

    /**
     * Set the value for the specified thread local map. The specified thread local map must be for the current thread.
     */
    public final void set(InternalThreadLocalMap threadLocalMap, V value) {
        getAndSet(threadLocalMap, value);
    }

    /**
     * Set the value for the current thread and returns the old value.
     */
    public V getAndSet(V value) {
        // value是否为默认值
        if (value != InternalThreadLocalMap.UNSET) {
            // 获取当前线程的InternalThreadLocalMap
            InternalThreadLocalMap threadLocalMap = InternalThreadLocalMap.get();
            // 将InternalThreadLocalMap中的数据替换为新的value
            return setKnownNotUnset(threadLocalMap, value);
        }
        return removeAndGet(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Set the value for the specified thread local map. The specified thread local map must be for the current thread.
     */
    public V getAndSet(InternalThreadLocalMap threadLocalMap, V value) {
        if (value != InternalThreadLocalMap.UNSET) {
            return setKnownNotUnset(threadLocalMap, value);
        }
        // 如果要设置的值是UNSET，直接清理
        return removeAndGet(threadLocalMap);
    }

    /**
     * @see InternalThreadLocalMap#setIndexedVariable(int, Object).
     */
    @SuppressWarnings("unchecked")
    private V setKnownNotUnset(InternalThreadLocalMap threadLocalMap, V value) {
        // 找到数组下标index，设置新的value
        V old = (V) threadLocalMap.getAndSetIndexedVariable(index, value);
        if (old == UNSET) {
            // 将FastThreadLocal对象保存到待清理的集合里
            addToVariablesToRemove(threadLocalMap, this);
            return null;
        }
        return old;
    }

    /**
     * Returns {@code true} if and only if this thread-local variable is set.
     */
    public final boolean isSet() {
        return isSet(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Returns {@code true} if and only if this thread-local variable is set.
     * The specified thread local map must be for the current thread.
     */
    public final boolean isSet(InternalThreadLocalMap threadLocalMap) {
        return threadLocalMap != null && threadLocalMap.isIndexedVariableSet(index);
    }
    /**
     * Sets the value to uninitialized for the specified thread local map and returns the old value.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     */
    public final void remove() {
        remove(InternalThreadLocalMap.getIfSet());
    }

    /**
     * Sets the value to uninitialized for the specified thread local map.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    public final void remove(InternalThreadLocalMap threadLocalMap) {
        removeAndGet(threadLocalMap);
    }

    /**
     * Sets the value to uninitialized for the specified thread local map.
     * After this, any subsequent call to get() will trigger a new call to initialValue().
     * The specified thread local map must be for the current thread.
     */
    @SuppressWarnings("unchecked")
    private V removeAndGet(InternalThreadLocalMap threadLocalMap) {
        if (threadLocalMap == null) {
            return null;
        }

        // 将指定索引的值置为UNSET，并返回旧值
        Object v = threadLocalMap.removeIndexedVariable(index);
        // 如果旧值不是UNSET，说明之前存过数据，也就意味着索引0的Set中有其对应的FastThreadLocal
        if (v != InternalThreadLocalMap.UNSET) {
            // 将其对应的FastThreadLocal对象从集合中移除
            removeFromVariablesToRemove(threadLocalMap, this);
            try {
                onRemoval((V) v);
            } catch (Exception e) {
                PlatformDependent.throwException(e);
            }
            return (V) v;
        }
        return null;
    }

    /**
     * Returns the initial value for this thread-local variable.
     */
    protected V initialValue() throws Exception {
        return null;
    }

    /**
     * Invoked when this thread local variable is removed by {@link #remove()}. Be aware that {@link #remove()}
     * is not guaranteed to be called when the `Thread` completes which means you can not depend on this for
     * cleanup of the resources in the case of `Thread` completion.
     */
    protected void onRemoval(@SuppressWarnings("UnusedParameters") V value) throws Exception { }
}
