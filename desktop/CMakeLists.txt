cmake_minimum_required(VERSION 3.21)
project(vados-gallery VERSION 1.0.0 LANGUAGES CXX)

set(CMAKE_CXX_STANDARD 20)
set(CMAKE_CXX_STANDARD_REQUIRED ON)

find_package(Qt6 6.8 REQUIRED COMPONENTS Core Gui Qml Quick QuickControls2 Svg Concurrent DBus ShaderTools)
# Video plays inline only with Qt Multimedia (pacman -S qt6-multimedia); without it a video opens in the system's player.
find_package(Qt6 6.8 QUIET OPTIONAL_COMPONENTS Multimedia)
find_package(PkgConfig REQUIRED)
pkg_check_modules(EXIF REQUIRED IMPORTED_TARGET libexif)
qt_standard_project_setup(REQUIRES 6.8)

qt_add_executable(vados-gallery
    src/main.cpp
)

file(GLOB GALLERY_GLYPHS CONFIGURE_DEPENDS RELATIVE ${CMAKE_CURRENT_SOURCE_DIR} assets/glyphs/*.svg)
file(GLOB_RECURSE GALLERY_QML CONFIGURE_DEPENDS RELATIVE ${CMAKE_CURRENT_SOURCE_DIR} qml/*.qml)
set(GALLERY_SINGLETONS qml/design/Theme.qml qml/design/Motion.qml qml/design/GlassSource.qml qml/design/FocusHome.qml qml/library/Sections.qml)
set_source_files_properties(${GALLERY_SINGLETONS} PROPERTIES QT_QML_SINGLETON_TYPE TRUE)
# The inline video player is only part of the module when Qt Multimedia is there to compile it against.
if(NOT Qt6Multimedia_FOUND)
    list(FILTER GALLERY_QML EXCLUDE REGEX "qml/viewer/VideoPlayer\\.qml$")
endif()

qt_add_qml_module(vados-gallery
    URI Gallery
    VERSION 1.0
    RESOURCE_PREFIX /qt/qml
    SOURCES
        src/core/mediaItem.h
        src/core/mediaFacts.h src/core/mediaFacts.cpp
        src/core/settings.h src/core/settings.cpp
        src/core/library.h src/core/library.cpp
        src/core/privateVault.h src/core/privateVault.cpp
        src/core/mediaActions.h src/core/mediaActions.cpp
        src/core/gridModel.h src/core/gridModel.cpp
        src/core/mediaEditor.h src/core/mediaEditor.cpp
        src/core/similarShots.h src/core/similarShots.cpp
        src/core/imagePrint.h src/core/imagePrint.cpp
        src/core/duplicateFinder.h src/core/duplicateFinder.cpp
        src/core/system.h src/core/system.cpp
        src/core/imageProviders.h src/core/imageProviders.cpp
        src/core/galleryService.h src/core/galleryService.cpp
    QML_FILES ${GALLERY_QML}
    RESOURCES ${GALLERY_GLYPHS}
)

qt_add_shaders(vados-gallery "shaders"
    PREFIX /shaders
    BASE shaders
    FILES
        shaders/squircle.frag
        shaders/squircleImage.frag
        shaders/fadeEdge.frag
        shaders/glyphInk.frag
)

file(GLOB GALLERY_FONTS RELATIVE ${CMAKE_CURRENT_SOURCE_DIR} assets/fonts/*.ttf assets/fonts/*.otf)
if(GALLERY_FONTS)
    qt_add_resources(vados-gallery "fonts" PREFIX / FILES ${GALLERY_FONTS})
endif()

target_compile_definitions(vados-gallery PRIVATE GALLERY_VERSION="${PROJECT_VERSION}")
target_include_directories(vados-gallery PRIVATE src/core)
target_link_libraries(vados-gallery PRIVATE
    Qt6::Core Qt6::Gui Qt6::Qml Qt6::Quick Qt6::QuickControls2 Qt6::Svg Qt6::Concurrent Qt6::DBus PkgConfig::EXIF)
if(Qt6Multimedia_FOUND)
    target_link_libraries(vados-gallery PRIVATE Qt6::Multimedia)
    target_compile_definitions(vados-gallery PRIVATE GALLERY_HAS_MULTIMEDIA)
endif()

install(TARGETS vados-gallery RUNTIME DESTINATION bin)
install(FILES packaging/vados-gallery.desktop DESTINATION share/applications)
install(FILES packaging/org.vados.Gallery.service DESTINATION share/dbus-1/services)
install(FILES packaging/icon/vados-gallery.svg DESTINATION share/icons/hicolor/scalable/apps)
