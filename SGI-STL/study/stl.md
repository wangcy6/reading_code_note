



# 版本

gcc 使用 4.8.4 版本，STL源码 在 Linux 系统的位置是：/usr/include/c++/4.8.4/bits (79个文件)

# 目录：

## [小王职场记 谈谈你的STL理解（1）](https://mp.weixin.qq.com/s/yOyLsW1PZfLZJqXeWR0Y6w)



# 算法部分

## 代码：

#include <algorithm>  

stl_algo.h 

## 函数对象(仿函数)

- 定义：

  重载了“operaotr()”操作符的普通类对象

```c++

 // 大于
template <class _Tp>
struct greater : public binary_function<_Tp,_Tp,bool> 
{
  bool operator()(const _Tp& __x, const _Tp& __y) const { return __x > __y; }
};
//这个函数对象没有数据成员、没有虚函数、没有显示声明的构造函数和析构函数，且对operator()的实现是内联的。用作STL比较器的函数对象一般都很小
```





- 函数对象作用： 回调函数

  > 使用函数对象作为比较器还有一个额外的好处，就是比较操作将被内联处理，
  >
  > 而使用函数指针则不允许内联

```c++
template< class RandomIt, class Compare > //calss 说明就是一个class 不是一个函数
void sort( RandomIt first, RandomIt last, Compare comp )
```

- 函数对象作用  ： 支持Lambda表达式

C++引入Lambda的最主要原因就是

1）可以定义匿名函数，

2）编译器会把其转成**函数对象**

【c++精神，不创造新概念】



![func_objets](https://github.com/wangcy6/reading_code_note/blob/master/SGI-STL/images/func_objets.PNG)

## std:sort 

```c++
template <class _RandomAccessIter>
inline void sort(_RandomAccessIter __first, _RandomAccessIter __last) {
  __STL_REQUIRES(_RandomAccessIter, _Mutable_RandomAccessIterator);
  __STL_REQUIRES(typename iterator_traits<_RandomAccessIter>::value_type,
                 _LessThanComparable);
  if (__first != __last) { //只有一个记录 ，不需要排序
    __introsort_loop(__first, __last,
                     __VALUE_TYPE(__first),
                     __lg(__last - __first) * 2);//快速排序，整体有序
    __final_insertion_sort(__first, __last); //剩下未排序的数据，直接插入排序
    
  }
}
template <class _RandomAccessIter, class _Tp, class _Size>
void __introsort_loop(_RandomAccessIter __first,
                      _RandomAccessIter __last, _Tp*,
                      _Size __depth_limit)
{
  while (__last - __first > __stl_threshold) { ///长度大于16才进行一次快排分割
    if (__depth_limit == 0) 
    {
      partial_sort(__first, __last, __last); //堆排序
      return;
    }
    --__depth_limit;
    _RandomAccessIter __cut =
      __unguarded_partition(__first, __last,
                            _Tp(__median(*__first,
                                         *(__first + (__last - __first)/2),
                                         *(__last - 1))));////找三个位置的中位数作为快排依据
    __introsort_loop(__cut, __last, (_Tp*) 0, __depth_limit); //排后一部分
    __last = __cut; //排前一部分
  }
}
```



### sort描述

维基百科 [内省排序](https://zh.wikipedia.org/wiki/%E5%86%85%E7%9C%81%E6%8E%92%E5%BA%8F)

**内省排序**（英语：Introsort）是由David Musser在1997年设计的[排序算法](https://zh.wikipedia.org/wiki/%E6%8E%92%E5%BA%8F%E7%AE%97%E6%B3%95)。

- 这个排序算法首先从[快速排序](https://zh.wikipedia.org/wiki/%E5%BF%AB%E9%80%9F%E6%8E%92%E5%BA%8F)开始，当递归深度超过一定深度（深度为排序元素数量的对数值）后转为[堆排序](https://zh.wikipedia.org/wiki/%E5%A0%86%E6%8E%92%E5%BA%8F)。采用这个方法，

内省排序既能在常规数据集上实现快速排序的高性能，又能在最坏情况下仍保持{\displaystyle O(nlogn)}![O(nlogn)](https://wikimedia.org/api/rest_v1/media/math/render/svg/e2f45af346af19e39ee9f58975dbab9740f475da)的[时间复杂度](https://zh.wikipedia.org/wiki/%E6%97%B6%E9%97%B4%E5%A4%8D%E6%9D%82%E5%BA%A6)。由于这两种算法都属于[比较排序](https://zh.wikipedia.org/wiki/%E6%AF%94%E8%BE%83%E6%8E%92%E5%BA%8F)算法，所以内省排序也是一个比较排序算法。

- 2000年6月，[SGI](https://zh.wikipedia.org/wiki/%E7%A1%85%E8%B0%B7%E5%9B%BE%E5%BD%A2%E5%85%AC%E5%8F%B8)的C++[标准模板库](https://zh.wikipedia.org/wiki/%E6%A0%87%E5%87%86%E6%A8%A1%E6%9D%BF%E5%BA%93)的[stl_algo.h](http://www.sgi.com/tech/stl/stl_algo.h)中的不稳定排序算法采用了Musser的内省排序算法。在此实现中，切换到插入排序的数据量阈值为16个。



主要因素：

if 递归深度 多大 then 改为堆排序 有不稳定排序改成稳定排序

if  数据少于16个 then 改为 插入排序，降低递归堆栈消耗





，因此Musser在1996年发表了一遍论文，提出了[Introspective Sorting](http://www.cs.rpi.edu/~musser/gp/index_1.html)(内省式排序)，这里可以找到[PDF版本](http://www.researchgate.net/profile/David_Musser/publication/2476873_Introspective_Sorting_and_Selection_Algorithms/file/3deec518194fb4a32f.pdf)。它是一种混合式的排序算法，集成了前面提到的三种算法各自的优点：

- 在数据量很大时采用正常的快速排序，此时效率为O(logN)。

- 一旦分段后的数据量小于某个阈值，就改用插入排序，因为此时这个分段是基本有序的，这时效率可达O(N)。

- 在递归过程中，如果递归层次过深，分割行为有恶化倾向时，它能够自动侦测出来，使用堆排序来处理，在此情况下，使其效率维持在堆排序的O(N logN)，但这又比一开始使用堆排序好。



  ### 复杂度

![1548126290890 (1)](../../images/1548126290890 (1).png)

参考

1. http://feihu.me/blog/2014/sgi-std-sort/
2. 动画：https://www.youtube.com/watch?v=67ta5WTjjUo
3. https://paste.ubuntu.com/p/Y8k2DKCTX5/
4. http://blog.sina.com.cn/s/blog_79d599dc01012m7l.html

## std::for_each



# 容器部分

# 分配器部分

# 迭代器部分



