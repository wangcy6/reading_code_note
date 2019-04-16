#include<cstdio>
#include<algorithm>
#include<map>
#include<iostream>
#include<vector>
 
using namespace std;
typedef pair<int, int> pii;
 
map<int, int> mp;
map<int, int>::iterator mit;
 
vector<pii> vc;
vector<pii>::iterator vit;
bool cmp(pii a, pii b) {
	return a.second < b.second;
}
int main() {
	mp[1] = 11;
	mp[2] = 2222;
	mp[3] = 333;
	mp[4] = 4;
	mp[0] = 888888;
	for(mit=mp.begin(); mit!=mp.end(); mit++) {
		vc.push_back(pii(mit->first, mit->second));
		cout<<mit->first<<"  "<<mit->second<<endl;
	}
	puts("-------------------------------------");
	
	sort(vc.begin(), vc.end(), cmp);
	for(vit=vc.begin(); vit!=vc.end(); vit++) {
		cout<<vit->first<<"  "<<vit->second<<endl;
	}
	return 0;
}
