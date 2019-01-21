# 版本

gcc 使用 4.8.4 版本，STL源码 在 Linux 系统的位置是：/usr/include/c++/4.8.4/bits (79个文件)



# 算法

## stl_algo.h

### 1. std:sort 

```c++
template <class _RandomAccessIter>
inline void sort(_RandomAccessIter __first, _RandomAccessIter __last) {

  if (__first != __last) {
    __introsort_loop(__first, __last,
                     __VALUE_TYPE(__first),
                     __lg(__last - __first) * 2);
    __final_insertion_sort(__first, __last);
  }
}
```









参考

1. http://feihu.me/blog/2014/sgi-std-sort/
2. 

# 容器

# 分配器

# 迭代器



