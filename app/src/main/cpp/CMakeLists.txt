# Copyright (c) 2026 Marco Morosi
#
# Licensed under the EUPL, Version 1.2 or - as soon they will be approved by
# the European Commission - subsequent versions of the EUPL (the "Licence");
# You may not use this work except in compliance with the Licence.
# You may obtain a copy of the Licence at:
#
# https://joinup.ec.europa.eu/software/page/eupl
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the Licence is distributed on an "AS IS" basis,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the Licence for the specific language governing permissions and
# limitations under the Licence.

# Native code of the app: libwfas3 only (WFAS v3 core, its cryptography and
# libopus). Every source file is checked against wfas3/MANIFEST.sha256 at
# configure time; the NDK provides jni.h.
cmake_minimum_required(VERSION 3.18)
project(wifiaudiostreaming_native C)

set(WFAS3_JNI_CLASS "com/cuscus/wifiaudiostreaming/wfas3/Wfas3Native")
set(WFAS3_OPUS_CLASS "com/cuscus/wifiaudiostreaming/wfas3/Wfas3Opus")
add_subdirectory(wfas3)
