import 'dart:async';
import 'dart:ui';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:platform/platform.dart';

part 'constants.dart';

typedef PhoneStateHandler(String phoneState);

void _flutterPhoneStateSetupBackgroundChannel(
    {MethodChannel backgroundChannel =
        const MethodChannel(_BACKGROUND_CHANNEL)}) async {
  WidgetsFlutterBinding.ensureInitialized();

  backgroundChannel.setMethodCallHandler((call) async {
    if (call.method == HANDLE_BACKGROUND_PHONE_STATE) {
      final CallbackHandle handle =
          CallbackHandle.fromRawHandle(call.arguments['handle']);
      final Function handlerFunction =
          PluginUtilities.getCallbackFromHandle(handle)!;
      try {
        await handlerFunction(call.arguments[ARG_PHONE_NUMBER]);
      } catch (e) {
        print('Unable to handle incoming background message.');
        print(e);
      }
      return Future<void>.value();
    }
  });

  backgroundChannel.invokeMethod<void>(BACKGROUND_SERVICE_INITIALIZED);
}

class PhoneState {
  final MethodChannel _foregroundChannel;
  final Platform _platform;

  late PhoneStateHandler _onNewPhoneState;
  late PhoneStateHandler _onBackgroundPhoneStateHandle;

  ///
  /// Gets a singleton instance of the [Telephony] class.
  ///
  static PhoneState get instance => _instance;

  ///
  /// Gets a singleton instance of the [Telephony] class to be used in background execution context.
  ///
  static PhoneState get backgroundInstance => _backgroundInstance;

  static const MethodChannel _channel = const MethodChannel('phone_state');

  static Future<String?> get platformVersion async {
    final String? version = await _channel.invokeMethod('getPlatformVersion');
    return version;
  }

  PhoneState._newInstance(MethodChannel methodChannel, LocalPlatform platform)
      : _foregroundChannel = methodChannel,
        _platform = platform {
    _foregroundChannel.setMethodCallHandler(handler);
  }

  static final PhoneState _instance = PhoneState._newInstance(
      const MethodChannel(_FOREGROUND_CHANNEL), const LocalPlatform());
  static final PhoneState _backgroundInstance = PhoneState._newInstance(
      const MethodChannel(_FOREGROUND_CHANNEL), const LocalPlatform());

  ///
  /// Listens to phone Call.
  ///
  /// ### Requires PHONE_STATE permission.
  ///
  /// Parameters:
  ///
  /// - [onNewPhoneState] : Called on every new phoneCall received when app is in foreground.
  /// - [onBackgroundPhoneState] (optional) : Called on every new phoneCall received when app is in background.
  /// - [listenInBackground] (optional) : Defaults to true. Set to false to only listen to phoneCall in foreground. [listenInBackground] is
  /// ignored if [onBackgroundMessage] is not set.
  ///
  ///
  void listenPhoneOffHook(
      {required PhoneStateHandler onNewPhoneState,
      PhoneStateHandler? onBackgroundPhoneStateHandle,
      bool listenInBackground = true}) {
    assert(_platform.isAndroid == true, "Can only be called on Android.");
    assert(
        listenInBackground
            ? onBackgroundPhoneStateHandle != null
            : onBackgroundPhoneStateHandle == null,
        listenInBackground
            ? "`onBackgroundPhoneState` cannot be null when `listenInBackground` is true. Set `listenInBackground` to false if you don't need background processing."
            : "You have set `listenInBackground` to false. `onBackgroundPhoneState` can only be set when `listenInBackground` is true");

    _onNewPhoneState = onNewPhoneState;

    if (listenInBackground && onBackgroundPhoneStateHandle != null) {
      _onBackgroundPhoneStateHandle = onBackgroundPhoneStateHandle;
      final CallbackHandle backgroundSetupHandle =
          PluginUtilities.getCallbackHandle(
              _flutterPhoneStateSetupBackgroundChannel)!;
      final CallbackHandle? backgroundPhoneStateHandle =
          PluginUtilities.getCallbackHandle(_onBackgroundPhoneStateHandle);

      if (backgroundPhoneStateHandle == null) {
        throw ArgumentError(
          '''Failed to setup background phoneState handler! `onBackgroundPhoneState`
          should be a TOP-LEVEL OR STATIC FUNCTION and should NOT be tied to a
          class or an anonymous function.''',
        );
      }

      _foregroundChannel.invokeMethod<bool>(
        'startBackgroundService',
        <String, dynamic>{
          'setupHandle': backgroundSetupHandle.toRawHandle(),
          'backgroundHandle': backgroundPhoneStateHandle.toRawHandle()
        },
      );
    } else {
      _foregroundChannel.invokeMethod('disableBackgroundService');
    }
  }

  ///
  /// Request the user for all the phone and sms permissions listed in the app's AndroidManifest.xml
  ///
  Future<bool?> get requestPhoneStatePermissions =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_PHONE_STATE_PERMISSION);

  Future<bool?> get requestRollCallScreen =>
      _foregroundChannel.invokeMethod<bool>(REQUEST_ROLL_CALL_SCREEN);

  /// ## Do not call this method. This method is visible only for testing.
  @visibleForTesting
  Future<dynamic> handler(MethodCall call) async {
    switch (call.method) {
      case ON_PHONE_OFF_HOOK:
        final message = call.arguments[ARG_PHONE_NUMBER];
        return _onNewPhoneState(message);
    }
  }

  Future<bool?> addContact({
    required String groupName,
    required String customerName,
    required String lastName,
    required String phoneNumber,
  }) async {
    assert(_platform.isAndroid == true, "Can only be called on Android.");

    final Map<String, dynamic> args = {
      ARG_PHONE_NUMBER: phoneNumber,
      ARG_GROUP_NAME: groupName,
      ARG_LAST_NAME: lastName,
      ARG_CUSTOMER_NAME: customerName
    };
    await _foregroundChannel.invokeMethod<bool>(
        INSERT_CUSTOMER_INTO_CONTACT, args);
  }
}
