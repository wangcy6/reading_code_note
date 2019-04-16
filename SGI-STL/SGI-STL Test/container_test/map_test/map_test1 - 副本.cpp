#include <string>
#include <algorithm>

#include <vector>       // std::vector

class finder
{
public:
    finder(const std::string &cmp_string) :s_(cmp_string){}
    bool operator ()(const std::map<int, std::string>::value_type &item)
    {
        return item.second == s_;
    }
private:
    const std::string &s_;
};
bool IsOdd (int i) {
  return ((i%2)==1);
}

class map_value_finder
{
public:
       map_value_finder(const std::string &cmp_string):m_s_cmp_string(cmp_string){}
       bool operator ()(const std::map<int, std::string>::value_type &pair)
       {
            return pair.second == m_s_cmp_string;
       }
private:
        const std::string &m_s_cmp_string;                    
};
//map是平衡二叉树，适用于快速检索。由于二叉平衡树插入机制以及内存分配的原因，它不适合频繁的内存操作
//https://gitee.com/zhuangtaiqiusi/map_perfomance_demo/blob/master/main.cpp
int main()
{
    std::map<int, std::string> my_map;
    my_map.insert(std::make_pair(10, "china"));
    my_map.insert(std::make_pair(20, "usa"));
    my_map.insert(std::make_pair(30, "english"));
    my_map.insert(std::make_pair(40, "hongkong"));    
    
    std::map<int, std::string>::iterator it = my_map.end();
    it = std::find_if(my_map.begin(), my_map.end(), map_value_finder("English"));
    if (it == my_map.end())
       printf("not found\n");       
    else
       printf("found key:%d value:%s\n", it->first, it->second.c_str());
       
	   
	std::vector<int> myvector;
	myvector.push_back(10);
	myvector.push_back(25);
	myvector.push_back(40);
	myvector.push_back(55);

	std::vector<int>::iterator it = std::find_if (myvector.begin(), myvector.end(), IsOdd);
	std::cout << "The first odd value is " << *it << '\n'
return 0;        
}
/**
cass finder
{
public:
    finder(const std::string &cmp_string) :s_(cmp_string){}
    bool operator ()(const std::map<int, std::string>::value_type &item)
    {
        return item.second == s_;
    }
private:
    const std::string &s_;
};


//调用
int n = 0;
auto it = std::find_if(t.begin(), t.end(), finder("d"));
    if (it != t.end())
    {
        n = (*it).first;
    }
--------------------- 
作者：flyfish1986 
来源：CSDN 
原文：https://blog.csdn.net/flyfish1986/article/details/72833001 
版权声明：本文为博主原创文章，转载请附上博文链接！
// find any element where x equal to 10
truct check_x
{
  check_x( int x ) : x_(x) {}
  bool operator()( const std::pair<int, ValueType>& v ) const 
  { 
    return v.second.x == x_; 
  }
private:
  int x_;
};
std::find_if( myMap.begin(), myMap.end(), check_x(10) );
**/