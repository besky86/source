#!/bin/env python
#-*- encoding=utf8 -*-
import time
import random
import hashlib
from qrcode.image.pure import PymagingImage
import sys
import nwbase_utils.util as util
import os
import qrcode
import string

appid = "wx204f69ced9c1292a"
mch_id = "1250893001"
key = "049ec3ed1d7fa7354b5fbeecefeb3980"
WORKING_PATH = os.getcwd()


RANDOM_BASE = string.digits + string.lowercase


def random_str(length):
    return ''.join(map(lambda x: random.choice(RANDOM_BASE), range(length)))


def generate_file_name():
    # "0ddab7000c1b3300"
    return random_str(12) + util.now().strftime("%Y%m%d%H%M%S")


def generate_qrcode():
    file_name = generate_file_name()
    file_name = hashlib.md5(file_name).hexdigest()[0:12]
    img = qrcode.make(file_name, image_factory=PymagingImage)
    now_dir = WORKING_PATH + "/" + util.now().strftime("%Y%m%d%H%M")
    if not os.path.exists(now_dir):
        os.mkdir(now_dir)
    img_path = now_dir + "/" + file_name + ".png"
    f = open(img_path, 'wb')
    img.save(f)
    f.close()

if __name__ == "__main__":
    for index in range(20):
        generate_qrcode()
    # if sys.platform.find('darwin') >= 0:
    #     os.system('open %s' % QRImagePath)
    # elif sys.platform.find('linux') >= 0:
    #     os.system('xdg-open %s' % QRImagePath)
    # else:
    #     os.system('call %s' % QRImagePath)
