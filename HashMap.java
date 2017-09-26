/**
 * HashMap类解读
 * 参考地址：
 * 美团技术团队：https://tech.meituan.com/java-hashmap.html
 * 红黑联盟：https://www.2cto.com/kf/201505/401433.html
 * @param <K>
 * @param <V>
 */

public class HashMap<K,V> extends AbstractMap<K,V>
        implements Map<K,V>, Cloneable, Serializable {

    private static final long serialVersionUID = 362498820763181265L;



    /**
     * 主要属性
     */

    //默认容量为16
    static final int DEFAULT_INITIAL_CAPACITY = 1 << 4; // aka 16

    /**
     * 最大容量
     * <<运算符的意义，表示移位操作，每次向左移动一位(相对于二进制来说)，表示乘以2
     * 此处1<<4表示00001中的1向左移动了4位，变成了10000，换算成十进制就是2^4=16，也就是HashMap的默认容量就是16。
     * Java中还有一些位操作符，比如类似的>>(右移)，还有>>>(无符号右移)等。
     * 这些位操作符的计算速度很快，我们在平时的工作中可以使用它们来提升我们系统的性能。
     */
    static final int MAXIMUM_CAPACITY = 1 << 30;

    /**
     * 默认负载因子为0.75,是对空间和时间效率的一个平衡选择
     * 当HashMap中存储的元素的数量大于(容量×加载因子)，也就是默认大于16*0.75=12时，HashMap会进行扩容的操作。
     */
    static final float DEFAULT_LOAD_FACTOR = 0.75f;

    //链表转成红黑树的阀值（当一个哈希桶／链表中节点超过该数值，将自动从维护一个链表转为维护一个哈希桶）
    static final int TREEIFY_THRESHOLD = 8;

    //红黑树转为链表的阀值
    static final int UNTREEIFY_THRESHOLD = 6;

    //存储方式由链表转成红黑树的容量的最小阈值
    static final int MIN_TREEIFY_CAPACITY = 64;

    /**
     * hashmap维护了一个每一个元素都是一个链表或者红黑树结构的数组
     * 当数据被Hash后，得到数组下标，把数据放在对应下标元素的链表上。
     */
    transient Node<K,V>[] table;




    /**
     * 构造函数就是对下面几个字段进行初始化
     */

    //HashMap中实际存在的键值对数量
    transient int size;

    /**
     * 所能容纳的key-value对极限  threshold = length * Load factor
     * 在数组定义好长度之后，负载因子越大，所能容纳的键值对个数越多。
     * 超过这个数目就重新resize(扩容)，扩容后的HashMap容量是之前容量的两倍。
     */
    int threshold;

    //负载因子
    final float loadFactor;

    //主要用来记录HashMap内部结构发生变化的次数，主要用于迭代的快速失败
    transient int modCount;



    /**
     * 四个构造函数
     */

    //指定“容量大小”和“加载因子”的构造函数
    public HashMap(int initialCapacity, float loadFactor) {
        if (initialCapacity < 0)
            throw new IllegalArgumentException("Illegal initial capacity: " +
                    initialCapacity);
        if (initialCapacity > MAXIMUM_CAPACITY)
            initialCapacity = MAXIMUM_CAPACITY;
        if (loadFactor <= 0 || Float.isNaN(loadFactor))
            throw new IllegalArgumentException("Illegal load factor: " +
                    loadFactor);
        this.loadFactor = loadFactor;
        this.threshold = tableSizeFor(initialCapacity);
    }

    //指定“容量大小”的构造函数
    public HashMap(int initialCapacity) {
        this(initialCapacity, DEFAULT_LOAD_FACTOR);
    }

    //默认构造函数
    public HashMap() {
        this.loadFactor = DEFAULT_LOAD_FACTOR; // all other fields defaulted
    }

    //包含“子Map”的构造函数
    public HashMap(Map<? extends K, ? extends V> m) {
        this.loadFactor = DEFAULT_LOAD_FACTOR;
        putMapEntries(m, false);
    }




    /**
     * Node节点的数据结构 继承自 Map.Entry<K,V>
     * @param <K>
     * @param <V>
     */
    static class Node<K,V> implements Map.Entry<K,V> {
        final int hash;  //用来定位数组索引位置
        final K key;
        V value;
        Node<K,V> next;  //维护一个向下的节点的引用

        Node(int hash, K key, V value, Node<K,V> next) {
            this.hash = hash;
            this.key = key;
            this.value = value;
            this.next = next;
        }

        public final K getKey()        { return key; }
        public final V getValue()      { return value; }
        public final String toString() { return key + "=" + value; }

        public final int hashCode() {
            return Objects.hashCode(key) ^ Objects.hashCode(value);
        }

        //赋值
        public final V setValue(V newValue) {
            V oldValue = value;
            value = newValue;
            return oldValue;
        }

        //equals方法
        public final boolean equals(Object o) {
            if (o == this)
                return true;
            if (o instanceof Map.Entry) {
                Map.Entry<?,?> e = (Map.Entry<?,?>)o;
                if (Objects.equals(key, e.getKey()) &&
                        Objects.equals(value, e.getValue()))
                    return true;
            }
            return false;
        }
    }


    /**
     *树节点数据结构
     * 即使负载因子和Hash算法设计的再合理，也免不了会出现拉链过长的情况，一旦出现拉链过长，则会严重影响HashMap的性能。
     * 于是，在JDK1.8版本中，对数据结构做了进一步的优化，引入了红黑树。而当链表长度太长（默认超过8）时，链表就转换为红黑树
     * 利用红黑树快速增删改查的特点提高HashMap的性能，其中会用到红黑树的插入、删除、查找等算法。
     */
    static final class TreeNode<K,V> extends LinkedHashMap.Entry<K,V> {
        TreeNode<K, V> parent;  //父节点
        TreeNode<K, V> left;    //左节点
        TreeNode<K, V> right;   //右节点
        TreeNode<K, V> prev;
        boolean red;            //对颜色的判断

        TreeNode(int hash, K key, V val, Node<K, V> next) {
            super(hash, key, val, next);
        }

        /**
         * 返回根节点
         */
        final TreeNode<K, V> root() {
            for (TreeNode<K, V> r = this, p; ; ) {
                if ((p = r.parent) == null)
                    return r;
                r = p;
            }
        }
    }


    /**
     * 确定哈希桶数组索引位置
     * 在这里可以了解为什么hashmap允许一个null为键，利用三元表达式，如果键为null，会赋一个默认值为0
     * @param key
     * @return
     */
    static final int hash(Object key) {
        int h;
        // h = key.hashCode() 为第一步 取hashCode值
        // h ^ (h >>> 16)  为第二步 高位参与运算
        return (key == null) ? 0 : (h = key.hashCode()) ^ (h >>> 16);
    }


    /**
     * put方法
     * @param key
     * @param value
     * @return
     */
    public V put(K key, V value) {
        //对key的hashCode()做hash
        return putVal(hash(key), key, value, false, true);
    }

    final V putVal(int hash, K key, V value, boolean onlyIfAbsent,
                   boolean evict) {
        Node<K,V>[] tab; Node<K,V> p; int n, i;
        //tab为空则调用resize方法扩容
        if ((tab = table) == null || (n = tab.length) == 0)
            n = (tab = resize()).length;
        //对键值进行计算，如果该位置哈希桶（链表）为空，直接实例化节点
        if ((p = tab[i = (n - 1) & hash]) == null)
            tab[i] = newNode(hash, key, value, null);
        else {
            //到此表明产生了哈希冲突，开始维护该哈希桶
            Node<K,V> e; K k;
            //如果该节点key已经存在于该哈希桶中，直接覆盖该键值对value
            if (p.hash == hash &&
                    ((k = p.key) == key || (key != null && key.equals(k))))
                e = p;
            //该节点key并没有已经存在在该哈希桶中，判断该链是否为红黑树，如果是直接添加树节点
            else if (p instanceof TreeNode)
                e = ((TreeNode<K,V>)p).putTreeVal(this, tab, hash, key, value);
            //到此说明该key没有存在于哈希桶中，也不是一个树节点的实例，是一个链表
            else {
                //开始遍历链表准备插入
                for (int binCount = 0; ; ++binCount) {
                    //p第一次指向表头,以后依次后移（if条件判断中括号内进行赋值操作）
                    if ((e = p.next) == null) {
                        //e为空，表示已到表尾也没有找到key值相同节点，则新建节点
                        p.next = newNode(hash, key, value, null);
                        //链表长度大于8转换为红黑树进行处理
                        if (binCount >= TREEIFY_THRESHOLD - 1) // -1 for 1st
                            treeifyBin(tab, hash);
                        break;
                    }
                    // 如果key已经存在直接覆盖value，//容许null==null
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        break;
                    p = e;//更新p指向下一个节点
                }
            }
            //更新hash值和key值均相同的节点Value值
            if (e != null) { // existing mapping for key
                V oldValue = e.value;
                if (!onlyIfAbsent || oldValue == null)
                    e.value = value;
                afterNodeAccess(e);
                return oldValue;
            }
        }
        ++modCount;
        // 超过最大容量 就扩容
        if (++size > threshold)
            resize();
        afterNodeInsertion(evict);
        return null;
    }




    /**
     * get方法
     */
    public V get(Object key) {
        Node<K,V> e;
        return (e = getNode(hash(key), key)) == null ? null : e.value;
    }

    final Node<K,V> getNode(int hash, Object key) {
        Node<K,V>[] tab; Node<K,V> first, e; int n; K k;
        //hash & (length-1)得到对象的保存位
        if ((tab = table) != null && (n = tab.length) > 0 &&
                (first = tab[(n - 1) & hash]) != null) {
            if (first.hash == hash && // always check first node
                    ((k = first.key) == key || (key != null && key.equals(k))))
                return first;
            if ((e = first.next) != null) {
                //如果第一个节点是TreeNode,说明采用的是数组+红黑树结构处理冲突
                //遍历红黑树，得到节点值
                if (first instanceof TreeNode)
                    return ((TreeNode<K,V>)first).getTreeNode(hash, key);
                //链表结构处理
                do {
                    if (e.hash == hash &&
                            ((k = e.key) == key || (key != null && key.equals(k))))
                        return e;
                } while ((e = e.next) != null);
            }
        }
        return null;
    }




    /**
     * 扩容机制
     * @return
     */
    final Node<K,V>[] resize() {
        Node<K,V>[] oldTab = table;
        //如果老的数组为空，老的数组容量设为0
        int oldCap = (oldTab == null) ? 0 : oldTab.length;
        int oldThr = threshold;
        int newCap, newThr = 0;
        //如果老的数组容量大于0，首先判断是否大于等于HashMap的最大容量，
        //如果true，将阈值设置为Integer的最大值，同时数组容量不变
        if (oldCap > 0) {
            // 超过最大值就不再扩充了，就只好随你碰撞去吧
            if (oldCap >= MAXIMUM_CAPACITY) {
                threshold = Integer.MAX_VALUE;
                return oldTab;
            }
            // 没超过最大值，就扩充为原来的2倍
            else if ((newCap = oldCap << 1) < MAXIMUM_CAPACITY &&
                    oldCap >= DEFAULT_INITIAL_CAP   ACITY)
                newThr = oldThr << 1; // double threshold
        }
        else if (oldThr > 0) // initial capacity was placed in threshold
            newCap = oldThr;
        else {               // zero initial threshold signifies using defaults
            newCap = DEFAULT_INITIAL_CAPACITY;
            newThr = (int)(DEFAULT_LOAD_FACTOR * DEFAULT_INITIAL_CAPACITY);
        }
        // 计算新的resize上限
        if (newThr == 0) {
            float ft = (float)newCap * loadFactor;
            newThr = (newCap < MAXIMUM_CAPACITY && ft < (float)MAXIMUM_CAPACITY ?
                    (int)ft : Integer.MAX_VALUE);
        }
        threshold = newThr;
        @SuppressWarnings({"rawtypes","unchecked"})
        Node<K,V>[] newTab = (Node<K,V>[])new Node[newCap];
        table = newTab;
        // 把每个bucket都移动到新的buckets中
        if (oldTab != null) {
            for (int j = 0; j < oldCap; ++j) {
                Node<K,V> e;
                if ((e = oldTab[j]) != null) {
                    oldTab[j] = null;
                    if (e.next == null)
                        newTab[e.hash & (newCap - 1)] = e;
                    else if (e instanceof TreeNode)
                        ((TreeNode<K,V>)e).split(this, newTab, j, oldCap);
                    else { // 链表优化重hash的代码块
                        Node<K,V> loHead = null, loTail = null;
                        Node<K,V> hiHead = null, hiTail = null;
                        Node<K,V> next;
                        do {
                            next = e.next;
                            if ((e.hash & oldCap) == 0) {
                                if (loTail == null)
                                    loHead = e;
                                else
                                    loTail.next = e;
                                loTail = e;
                            }
                            else {
                                if (hiTail == null)
                                    hiHead = e;
                                else
                                    hiTail.next = e;
                                hiTail = e;
                            }
                        } while ((e = next) != null);
                        if (loTail != null) {
                            loTail.next = null;
                            newTab[j] = loHead;
                        }
                        if (hiTail != null) {
                            hiTail.next = null;
                            newTab[j + oldCap] = hiHead;
                        }
                    }
                }
            }
        }
        return newTab;
    }

    /**
     * Returns a power of two size for the given target capacity.
     */
    static final int tableSizeFor(int cap) {
        int n = cap - 1;
        n |= n >>> 1;
        n |= n >>> 2;
        n |= n >>> 4;
        n |= n >>> 8;
        n |= n >>> 16;
        return (n < 0) ? 1 : (n >= MAXIMUM_CAPACITY) ? MAXIMUM_CAPACITY : n + 1;
    }

    transient Set<Map.Entry<K,V>> entrySet;


    final void putMapEntries(Map<? extends K, ? extends V> m, boolean evict) {
        int s = m.size();
        if (s > 0) {
            if (table == null) { // pre-size
                float ft = ((float)s / loadFactor) + 1.0F;
                int t = ((ft < (float)MAXIMUM_CAPACITY) ?
                        (int)ft : MAXIMUM_CAPACITY);
                if (t > threshold)
                    threshold = tableSizeFor(t);
            }
            else if (s > threshold)
                resize();
            for (Map.Entry<? extends K, ? extends V> e : m.entrySet()) {
                K key = e.getKey();
                V value = e.getValue();
                putVal(hash(key), key, value, false, evict);
            }
        }
    }

}