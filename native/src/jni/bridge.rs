use jni::JNIEnv;
use jni::objects::{JClass, JString, JByteArray};
use jni::sys::{jlong, jboolean, jfloat, jbyteArray, jint};

use crate::player::player_engine::PlayerEngine;

#[inline]
unsafe fn get_engine<'a>(handle: jlong) -> Option<&'a mut PlayerEngine> {
    if handle == 0 {
        None
    } else {
        Some(&mut *(handle as *mut PlayerEngine))
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeInit(
    _env: JNIEnv,
    _class: JClass,
) -> jlong {
    let engine = Box::new(PlayerEngine::new());
    Box::into_raw(engine) as jlong
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeOpenFile(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    file_path: JString,
) -> jboolean {
    let engine = match unsafe { get_engine(handle) } {
        Some(e) => e,
        None => return false as jboolean,
    };
    let path: String = env.get_string(&file_path).unwrap().into();

    match engine.open_file(&path) {
        Ok(_) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeOpenUrl(
    mut env: JNIEnv,
    _class: JClass,
    handle: jlong,
    url: JString,
) -> jboolean {
    let engine = match unsafe { get_engine(handle) } {
        Some(e) => e,
        None => return false as jboolean,
    };
    let url_str: String = env.get_string(&url).unwrap().into();

    match engine.open_url(&url_str) {
        Ok(_) => true as jboolean,
        Err(_) => false as jboolean,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativePlay(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        engine.play();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativePause(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        engine.pause();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeStop(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        engine.stop();
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeSeek(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    position_ms: jlong,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        let _ = engine.seek(position_ms as u64);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeGetPosition(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    match unsafe { get_engine(handle) } {
        Some(engine) => engine.get_position() as jlong,
        None => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeGetDuration(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jlong {
    match unsafe { get_engine(handle) } {
        Some(engine) => engine.get_duration() as jlong,
        None => 0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeSetVolume(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    volume: jfloat,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        engine.set_volume(volume);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeGetVolume(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) -> jfloat {
    match unsafe { get_engine(handle) } {
        Some(engine) => engine.get_volume(),
        None => 0.0,
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeSetSpeed(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
    speed: jfloat,
) {
    if let Some(engine) = unsafe { get_engine(handle) } {
        engine.set_speed(speed);
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeGetFrame(
    env: JNIEnv,
    _class: JClass,
    handle: jlong,
    buffer: jbyteArray,
    width: jint,
    height: jint,
) -> jboolean {
    let engine = match unsafe { get_engine(handle) } {
        Some(e) => e,
        None => return false as jboolean,
    };

    let mut vec = vec![0u8; (width * height * 3) as usize];
    let result = engine.get_frame(&mut vec, width as u32, height as u32);

    if result {
        let java_array = unsafe { JByteArray::from_raw(buffer) };
        let i8_slice: &[i8] = unsafe { std::slice::from_raw_parts(vec.as_ptr() as *const i8, vec.len()) };
        env.set_byte_array_region(&java_array, 0, i8_slice).unwrap();
        true as jboolean
    } else {
        false as jboolean
    }
}

#[no_mangle]
pub extern "system" fn Java_com_floatplay_service_NativeBridge_nativeDestroy(
    _env: JNIEnv,
    _class: JClass,
    handle: jlong,
) {
    if handle == 0 {
        return;
    }
    unsafe {
        let _ = Box::from_raw(handle as *mut PlayerEngine);
    }
}
