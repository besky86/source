#!/bin/env python
#-*- encoding=utf8 -*-
import time
import winsound

import requests


requests.packages.urllib3.disable_warnings()


def q(data):
    url = 'https://kyfw.12306.cn/otn/leftTicket/queryT?leftTicketDTO.train_date=%(date)s&leftTicketDTO.from_station=%(from)s&leftTicketDTO.to_station=%(to)s&purpose_codes=ADULT'
    r = requests.get(url % data, verify=False)
    json_obj = r.json()
    if json_obj and json_obj['status']:
        train_list = json_obj['data']
        for each_train in train_list:
            train_info = each_train['queryLeftNewDTO']

            train_code = train_info['station_train_code']
            if not train_code.startswith('G'):
                # 非高铁
                continue
            start_time = train_info['start_time']
            start_hour = int(start_time[:2])
            if start_hour < data.get('start', 8):
                # 出发时间
                continue
            gg_num = train_info.get('gg_num', '--')  
            gr_num = train_info.get('gr_num', '--')  # 高软
            qt_num = train_info.get('qt_num', '--')  
            rw_num = train_info.get('rw_num', '--')  # 软卧
            rz_num = train_info.get('rz_num', '--')  # 软座
            tz_num = train_info.get('tz_num', '--')  # 特等座
            wz_num = train_info.get('wz_num', '--')  # 无座
            yb_num = train_info.get('yb_num', '--')
            yw_num = train_info.get('yw_num', '--')  # 硬卧
            yz_num = train_info.get('yz_num', '--')  # 硬座
            ze_num = train_info.get('ze_num', '--')  # 二等座
            zy_num = train_info.get('zy_num', '--')  # 一等座
            swz_num = train_info.get('swz_num', '--') # 商务座
            if ze_num not in ('--', u'无', '*'):
                winsound.Beep(800, 1500)
                print 'got_ticket_%s,%s,%s' % (train_code,ze_num,data)


if __name__ == '__main__':
    winsound.Beep(800, 1500)
    while True:
        # 岳阳到广州南
        q({'date': '2016-02-16', "from": 'YYQ', "to": 'IZQ', 'start':8})
        q({'date': '2016-02-17', "from": 'YYQ', "to": 'IZQ', 'start':8})
        q({'date': '2016-02-18', "from": 'YYQ', "to": 'IZQ', 'start':8})
        # 岳阳到深圳
        q({'date': '2016-02-16', "from": 'YYQ', "to": 'SZQ', 'start':8})
        q({'date': '2016-02-17', "from": 'YYQ', "to": 'SZQ', 'start':8})
        q({'date': '2016-02-18', "from": 'YYQ', "to": 'SZQ', 'start':8})
        # # 武汉到广州南
        # q({'date': '2016-02-16', "from": 'WHN', "to": 'IZQ'})
        # q({'date': '2016-02-17', "from": 'WHN', "to": 'IZQ'})
        # q({'date': '2016-02-18', "from": 'WHN', "to": 'IZQ'})
        # 武汉到深圳
        # q({'date': '2016-02-16', "from": 'WHN', "to": 'SZQ', 'start':12})
        # q({'date': '2016-02-17', "from": 'WHN', "to": 'SZQ', 'start':12})
        # q({'date': '2016-02-18', "from": 'WHN', "to": 'SZQ', 'start':12})
        print 'wait next'
        time.sleep(3)
    