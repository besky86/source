import sublime, sublime_plugin, os, subprocess

class PythonBuildCommand(sublime_plugin.TextCommand):
    def run(self, edit):
        if self.view.file_name():
            folder_name, file_name = os.path.split(self.view.file_name())
        if file_name.endswith('py'):
            app_file_path = folder_name + "\\app.py"
            target_folder_name = folder_name

            if not os.path.isfile(app_file_path):
                app_file_path = folder_name + '\\..\\app.py'
                target_folder_name = target_folder_name + '\\..'
            if not os.path.isfile(app_file_path):
                app_file_path = folder_name + '\\..\\..\\app.py'
                target_folder_name = target_folder_name + '\\..\\..'

            if not os.path.isfile(app_file_path):
                command = 'cmd /k python %s\\%s' % (folder_name, file_name)
                target_folder_name = folder_name
            else:
                command = 'cmd /k python %s' % app_file_path
            process1 = subprocess.Popen(command, cwd=target_folder_name)
        elif file_name.endswith('html'):
            command = 'cmd /k start ' + folder_name + "/" + file_name + ' && exit'
            process1 = subprocess.Popen(command)
            print(dir(process1))

