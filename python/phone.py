#!/bin/env python
#-*- encoding=utf8 -*-
import sys
reload(sys)
sys.setdefaultencoding("utf-8")
import urllib
import urllib2
import json


def print_obj(dict_obj):
    for key in dict_obj:
        print key
        value = dict_obj[key]
        if isinstance(value, dict):
            print_obj(value)
        print str(key) + ":" + str(value)


url = 'http://apis.baidu.com/baidu_mobile_security/phone_number_service/phone_information_query?tel=13600190858%2C03936038331&location=true'


req = urllib2.Request(url)

req.add_header("apikey", "429964b36eb404913951e7cc69809d9b")

resp = urllib2.urlopen(req)
content = resp.read()
if(content):
    content = json.loads(content)
    print_obj(content)
