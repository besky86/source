# -*- coding: utf-8 -*-
# flake8: noqa
import os
import string
import random
import tempfile
import requests

import unittest
import pytest

from qiniu import Auth, set_default, etag, PersistentFop, build_op, op_save, Zone
from qiniu import put_data, put_file, put_stream
from qiniu import BucketManager, build_batch_copy, build_batch_rename, build_batch_move, build_batch_stat, build_batch_delete
from qiniu import urlsafe_base64_encode, urlsafe_base64_decode

from qiniu.compat import is_py2, is_py3, b

from qiniu.services.storage.uploader import _form_put

import qiniu.config

if is_py2:
    import sys
    import StringIO
    import urllib
    reload(sys)
    sys.setdefaultencoding('utf-8')
    StringIO = StringIO.StringIO
    urlopen = urllib.urlopen
elif is_py3:
    import io
    import urllib
    StringIO = io.StringIO
    urlopen = urllib.request.urlopen

access_key = '8QRCjxB5D2osv5UJx5kFJgUc3qP4LfocpN5H9-Vb'  # os.getenv('QINIU_ACCESS_KEY')
secret_key = 'vqu1x-xsMmA5zWCNbe0PQmDyarcqKeln3jqV5t7e'  # os.getenv('QINIU_SECRET_KEY')
bucket_name = "posuoren"  # os.getenv('QINIU_TEST_BUCKET')

dummy_access_key = '8QRCjxB5D2osv5UJx5kFJgUc3qP4LfocpN5H9-Vb'
dummy_secret_key = 'vqu1x-xsMmA5zWCNbe0PQmDyarcqKeln3jqV5t7e'
dummy_auth = Auth(dummy_access_key, dummy_secret_key)


def rand_string(length):
    lib = string.ascii_uppercase
    return ''.join([random.choice(lib) for i in range(0, length)])


def create_temp_file(size):
    t = tempfile.mktemp()
    f = open(t, 'wb')
    f.seek(size - 1)
    f.write(b('0'))
    f.close()
    return t


def remove_temp_file(file):
    try:
        os.remove(file)
    except OSError:
        pass


# class UtilsTest(unittest.TestCase):

#     def test_urlsafe(self):
#         a = '你好\x96'
#         u = urlsafe_base64_encode(a)
#         assert b(a) == urlsafe_base64_decode(u)


# class AuthTestCase(unittest.TestCase):

#     def test_token(self):
#         token = dummy_auth.token('test')
#         assert token == 'abcdefghklmnopq:mSNBTR7uS2crJsyFr2Amwv1LaYg='

#     def test_token_with_data(self):
#         token = dummy_auth.token_with_data('test')
#         assert token == 'abcdefghklmnopq:-jP8eEV9v48MkYiBGs81aDxl60E=:dGVzdA=='

#     def test_noKey(self):
#         with pytest.raises(ValueError):
#             Auth(None, None).token('nokey')
#         with pytest.raises(ValueError):
#             Auth('', '').token('nokey')

#     def test_token_of_request(self):
#         token = dummy_auth.token_of_request('http://www.qiniu.com?go=1', 'test', '')
#         assert token == 'abcdefghklmnopq:cFyRVoWrE3IugPIMP5YJFTO-O-Y='
#         token = dummy_auth.token_of_request('http://www.qiniu.com?go=1', 'test', 'application/x-www-form-urlencoded')
#         assert token == 'abcdefghklmnopq:svWRNcacOE-YMsc70nuIYdaa1e4='

#     def test_deprecatedPolicy(self):
#         with pytest.raises(ValueError):
#             dummy_auth.upload_token('1', None, policy={'asyncOps': 1})

#     def test_verify_callback(self):
#         body = 'name=sunflower.jpg&hash=Fn6qeQi4VDLQ347NiRm-RlQx_4O2&location=Shanghai&price=1500.00&uid=123'
#         url = 'test.qiniu.com/callback'
#         ok = dummy_auth.verify_callback('QBox abcdefghklmnopq:ZWyeM5ljWMRFwuPTPOwQ4RwSto4=', url, body)
#         assert ok


# class BucketTestCase(unittest.TestCase):


def bucket_list():
    q = Auth(access_key, secret_key)
    bucket = BucketManager(q)
    ret, eof, info = bucket.list(bucket_name, limit=4)
    for item in ret.get('items', []):
        print item
    ret, eof, info = bucket.list(bucket_name, limit=100)
    print(info)

#     def test_buckets(self):
#         ret, info = self.bucket.buckets()
#         print(info)
#         assert bucket_name in ret

#     def test_prefetch(self):
#         ret, info = self.bucket.prefetch(bucket_name, 'python-sdk.html')
#         print(info)
#         assert ret['key'] == 'python-sdk.html'

#     def test_fetch(self):
#         ret, info = self.bucket.fetch('http://developer.qiniu.com/docs/v6/sdk/python-sdk.html', bucket_name, 'fetch.html')
#         print(info)
#         assert ret['key'] == 'fetch.html'
#         assert 'hash' in ret

#     def test_fetch_without_key(self):
#         ret, info = self.bucket.fetch('http://developer.qiniu.com/docs/v6/sdk/python-sdk.html', bucket_name)
#         print(info)
#         assert ret['key'] == ret['hash']
#         assert 'hash' in ret

#     def test_stat(self):
#         ret, info = self.bucket.stat(bucket_name, 'python-sdk.html')
#         print(info)
#         assert 'hash' in ret

#     def test_delete(self):
#         ret, info = self.bucket.delete(bucket_name, 'del')
#         print(info)
#         assert ret is None
#         assert info.status_code == 612

#     def test_rename(self):
#         key = 'renameto' + rand_string(8)
#         self.bucket.copy(bucket_name, 'copyfrom', bucket_name, key)
#         key2 = key + 'move'
#         ret, info = self.bucket.rename(bucket_name, key, key2)
#         print(info)
#         assert ret == {}
#         ret, info = self.bucket.delete(bucket_name, key2)
#         print(info)
#         assert ret == {}

#     def test_copy(self):
#         key = 'copyto' + rand_string(8)
#         ret, info = self.bucket.copy(bucket_name, 'copyfrom', bucket_name, key)
#         print(info)
#         assert ret == {}
#         ret, info = self.bucket.delete(bucket_name, key)
#         print(info)
#         assert ret == {}

#     def test_change_mime(self):
#         ret, info = self.bucket.change_mime(bucket_name, 'python-sdk.html', 'text/html')
#         print(info)
#         assert ret == {}

#     def test_batch_copy(self):
#         key = 'copyto' + rand_string(8)
#         ops = build_batch_copy(bucket_name, {'copyfrom': key}, bucket_name)
#         ret, info = self.bucket.batch(ops)
#         print(info)
#         assert ret[0]['code'] == 200
#         ops = build_batch_delete(bucket_name, [key])
#         ret, info = self.bucket.batch(ops)
#         print(info)
#         assert ret[0]['code'] == 200

#     def test_batch_move(self):
#         key = 'moveto' + rand_string(8)
#         self.bucket.copy(bucket_name, 'copyfrom', bucket_name, key)
#         key2 = key + 'move'
#         ops = build_batch_move(bucket_name, {key: key2}, bucket_name)
#         ret, info = self.bucket.batch(ops)
#         print(info)
#         assert ret[0]['code'] == 200
#         ret, info = self.bucket.delete(bucket_name, key2)
#         print(info)
#         assert ret == {}

#     def test_batch_rename(self):
#         key = 'rename' + rand_string(8)
#         self.bucket.copy(bucket_name, 'copyfrom', bucket_name, key)
#         key2 = key + 'rename'
#         ops = build_batch_move(bucket_name, {key: key2}, bucket_name)
#         ret, info = self.bucket.batch(ops)
#         print(info)
#         assert ret[0]['code'] == 200
#         ret, info = self.bucket.delete(bucket_name, key2)
#         print(info)
#         assert ret == {}

#     def test_batch_stat(self):
#         ops = build_batch_stat(bucket_name, ['python-sdk.html'])
#         ret, info = self.bucket.batch(ops)
#         print(info)
#         assert ret[0]['code'] == 200


# class UploaderTestCase(unittest.TestCase):

#     mime_type = "image/png"
#     params = {'x:a': 'a'}
#     q = Auth(access_key, secret_key)

#     # def test_put(self):
#     #     key = 'a\\b\\c"你好'
#     #     data = 'hello bubby!'
#     #     token = self.q.upload_token(bucket_name)
#     #     ret, info = put_data(token, key, data)
#     #     print(info)
#     #     assert ret['key'] == key

#     #     key = ''
#     #     data = 'hello bubby!'
#     #     token = self.q.upload_token(bucket_name, key)
#     #     ret, info = put_data(token, key, data, check_crc=True)
#     #     print(info)
#     #     assert ret['key'] == key

def putfile(file_url):
    q = Auth(access_key, secret_key)
    bucket = BucketManager(q)
    mime_type, file_type = get_mime_type(file_url)
    key = etag(file_url) + "." + file_type  # "test"
    token = q.upload_token(bucket_name, key)
    ret, info = put_file(token, key, file_url, mime_type=mime_type, check_crc=True)
    print ret

    # def test_putInvalidCrc(self):
    #     key = 'test_invalid'
    #     data = 'hello bubby!'
    #     crc32 = 'wrong crc32'
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = _form_put(token, key, data, None, None, crc=crc32)
    #     print(info)
    #     assert ret is None
    #     assert info.status_code == 400

    # def test_putWithoutKey(self):
    #     key = None
    #     data = 'hello bubby!'
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = put_data(token, key, data)
    #     print(info)
    #     assert ret['hash'] == ret['key']

    #     data = 'hello bubby!'
    #     token = self.q.upload_token(bucket_name, 'nokey2')
    #     ret, info = put_data(token, None, data)
    #     print(info)
    #     assert ret is None
    #     assert info.status_code == 403  # key not match

    # def test_withoutRead_withoutSeek_retry(self):
    #     key = 'retry'
    #     data = 'hello retry!'
    #     set_default(default_zone=Zone('a', 'upload.qiniu.com'))
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = put_data(token, key, data)
    #     print(info)
    #     assert ret['key'] == key
    #     assert ret['hash'] == 'FlYu0iBR1WpvYi4whKXiBuQpyLLk'
    #     qiniu.set_default(default_zone=qiniu.config.zone0)

    # def test_hasRead_hasSeek_retry(self):
    #     key = 'withReadAndSeek_retry'
    #     data = StringIO('hello retry again!')
    #     set_default(default_zone=Zone('a', 'upload.qiniu.com'))
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = put_data(token, key, data)
    #     print(info)
    #     assert ret['key'] == key
    #     assert ret['hash'] == 'FuEbdt6JP2BqwQJi7PezYhmuVYOo'
    #     qiniu.set_default(default_zone=qiniu.config.zone0)

    # def test_hasRead_withoutSeek_retry(self):
    #     key = 'withReadAndWithoutSeek_retry'
    #     data = ReadWithoutSeek('I only have read attribute!')
    #     set_default(default_zone=Zone('a', 'upload.qiniu.com'))
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = put_data(token, key, data)
    #     print(info)
    #     assert ret is None
    #     qiniu.set_default(default_zone=qiniu.config.zone0)

    # def test_hasRead_WithoutSeek_retry2(self):
    #     key = 'withReadAndWithoutSeek_retry2'
    #     data = urlopen("http://www.qiniu.com")
    #     set_default(default_zone=Zone('a', 'upload.qiniu.com'))
    #     token = self.q.upload_token(bucket_name)
    #     ret, info = put_data(token, key, data)
    #     print(info)
    #     assert ret is None
    #     qiniu.set_default(default_zone=qiniu.config.zone0)


# class ResumableUploaderTestCase(unittest.TestCase):

#     mime_type = "text/plain"
#     params = {'x:a': 'a'}
#     q = Auth(access_key, secret_key)

#     def test_put_stream(self):
#         localfile = __file__
#         key = 'test_file_r'
#         size = os.stat(localfile).st_size
#         with open(localfile, 'rb') as input_stream:
#             token = self.q.upload_token(bucket_name, key)
#             ret, info = put_stream(token, key, input_stream, size, self.params, self.mime_type)
#             print(info)
#             assert ret['key'] == key

#     def test_big_file(self):
#         key = 'big'
#         token = self.q.upload_token(bucket_name, key)
#         localfile = create_temp_file(4 * 1024 * 1024 + 1)
#         progress_handler = lambda progress, total: progress
#         qiniu.set_default(default_zone=Zone('a', 'upload.qiniu.com'))
#         ret, info = put_file(token, key, localfile, self.params, self.mime_type, progress_handler=progress_handler)
#         print(info)
#         assert ret['key'] == key
#         qiniu.set_default(default_zone=qiniu.config.zone0)
#         remove_temp_file(localfile)

#     def test_retry(self):
#         localfile = __file__
#         key = 'test_file_r_retry'
#         qiniu.set_default(default_zone=Zone('a', 'upload.qiniu.com'))
#         token = self.q.upload_token(bucket_name, key)
#         ret, info = put_file(token, key, localfile, self.params, self.mime_type)
#         print(info)
#         assert ret['key'] == key
#         assert ret['hash'] == etag(localfile)
#         qiniu.set_default(default_zone=qiniu.config.zone0)


# class DownloadTestCase(unittest.TestCase):

#     q = Auth(access_key, secret_key)

#     def test_private_url(self):
#         private_bucket = 'private-res'
#         private_key = 'gogopher.jpg'
#         base_url = 'http://%s/%s' % (private_bucket + '.qiniudn.com', private_key)
#         private_url = self.q.private_download_url(base_url, expires=3600)
#         print(private_url)
#         r = requests.get(private_url)
#         assert r.status_code == 200


# class MediaTestCase(unittest.TestCase):

#     def test_pfop(self):
#         q = Auth(access_key, secret_key)
#         pfop = PersistentFop(q, 'testres', 'sdktest')
#         op = op_save('avthumb/m3u8/segtime/10/vcodec/libx264/s/320x240', 'pythonsdk', 'pfoptest')
#         ops = []
#         ops.append(op)
#         ret, info = pfop.execute('sintel_trailer.mp4', ops, 1)
#         print(info)
#         assert ret['persistentId'] is not None


class ReadWithoutSeek(object):

    def __init__(self, str):
        self.str = str
        pass

    def read(self):
        print(self.str)

type_list = {
    'html':       'text/html',
    'htm':       'text/html',
    'shtml':       'text/html',
    'css':       'text/css',
    'xml':       'text/xml',
    'gif':       'image/gif',
    'jpeg':       'image/jpeg',
    'jpg':       'image/jpeg',
    'js':       'application/x-javascript',
    'atom':       'application/atom+xml',
    'rss':       'application/rss+xml',
    'mml':       'text/mathml',
    'txt':       'text/plain',
    'jad':       'text/vnd.sun.j2me.app-descriptor',
    'wml':       'text/vnd.wap.wml',
    'htc':       'text/x-component',
    'png':       'image/png',
    'tif':       'image/tiff',
    'tiff':       'image/tiff',
    'wbmp':       'image/vnd.wap.wbmp',
    'ico':       'image/x-icon',
    'jng':       'image/x-jng',
    'bmp':       'image/x-ms-bmp',
    'svg':       'image/svg+xml',
    'svgz':       'image/svg+xml',
    'webp':       'image/webp',
    'jar':       'application/java-archive',
    'war':       'application/java-archive',
    'ear':       'application/java-archive',
    'hqx':       'application/mac-binhex40',
    'doc':       'application/msword',
    'pdf':       'application/pdf',
    'ps':       'application/postscript',
    'eps':       'application/postscript',
    'ai':       'application/postscript',
    'rtf':       'application/rtf',
    'xls':       'application/vnd.ms-excel',
    'ppt':       'application/vnd.ms-powerpoint',
    'wmlc':       'application/vnd.wap.wmlc',
    'kml':       'application/vnd.google-earth.kml+xml',
    'kmz':       'application/vnd.google-earth.kmz',
    '7z':       'application/x-7z-compressed',
    'cco':       'application/x-cocoa',
    'jardiff':       'application/x-java-archive-diff',
    'jnlp':       'application/x-java-jnlp-file',
    'run':       'application/x-makeself',
    'pl':       'application/x-perl',
    'pm':       'application/x-perl',
    'prc':       'application/x-pilot',
    'pdb':       'application/x-pilot',
    'rar':       'application/x-rar-compressed',
    'rpm':       'application/x-redhat-package-manager',
    'sea':       'application/x-sea',
    'swf':       'application/x-shockwave-flash',
    'sit':       'application/x-stuffit',
    'tcl':       'application/x-tcl',
    'tk':       'application/x-tcl',
    'der':       'application/x-x509-ca-cert',
    'pem':       'application/x-x509-ca-cert',
    'crt':       'application/x-x509-ca-cert',
    'xpi':       'application/x-xpinstall',
    'xhtml':       'application/xhtml+xml',
    'zip':       'application/zip',
    'bin':       'application/octet-stream',
    'exe':       'application/octet-stream',
    'dll':       'application/octet-stream',
    'deb':       'application/octet-stream',
    'dmg':       'application/octet-stream',
    'eot':       'application/octet-stream',
    'iso':       'application/octet-stream',
    'img':       'application/octet-stream',
    'msi':       'application/octet-stream',
    'msp':       'application/octet-stream',
    'msm':       'application/octet-stream',
    'mid':       'audio/midi',
    'midi':       'audio/midi',
    'kar':       'audio/midi',
    'mp3':       'audio/mpeg',
    'ogg':       'audio/ogg',
    'm4a':       'audio/x-m4a',
    'ra':       'audio/x-realaudio',
    '3gpp':       'video/3gpp',
    '3gp':       'video/3gpp',
    'mp4':       'video/mp4',
    'mpeg':       'video/mpeg',
    'mpg':       'video/mpeg',
    'mov':       'video/quicktime',
    'webm':       'video/webm',
    'flv':       'video/x-flv',
    'm4v':       'video/x-m4v',
    'mng':       'video/x-mng',
    'asx':       'video/x-ms-asf',
    'asf':       'video/x-ms-asf',
    'wmv':       'video/x-ms-wmv',
    'avi':       'video/x-msvideo'
}


def get_mime_type(filename):  # filename 文件路径

    # 返回文件路径后缀名
    filename_type = os.path.splitext(filename)[1][1:]

    # 判断数据中是否有该后缀名的 key
    if (filename_type in type_list.keys()):
        return type_list[filename_type], filename_type
    else:
        return ''


if __name__ == '__main__':
    print str(sys.argv[1:])
    # putfile(sys.argv[1:][0])
    bucket_list()
    # unittest.main()
