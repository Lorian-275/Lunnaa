from release import read_version

assert read_version('versionCode = 12\n        versionName = "1.3"') == (12, "1.3")
try:
    read_version("sin versión")
    raise AssertionError("debió fallar")
except ValueError:
    pass
print("ok")
