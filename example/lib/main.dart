import 'package:flutter/material.dart';
import 'dart:async';

import 'package:flutter/services.dart';
import 'package:phone_state/phone_state.dart';
import 'package:shared_preferences/shared_preferences.dart';

Future<void> onPhoneBg(String phoneNumber) async {
  SharedPreferences prefs = await SharedPreferences.getInstance();
  await prefs.setString('phone', phoneNumber);
}

void main() {
  runApp(MyApp());
}

class MyApp extends StatefulWidget {
  @override
  _MyAppState createState() => _MyAppState();
}

class _MyAppState extends State<MyApp> {
  String _platformVersion = 'Unknown';
  String phoneNumber = 'Unknown';
  final phoneState = PhoneState.instance;

  @override
  void initState() {
    super.initState();
    initPlatformState();
    loadPhoneNumber();
  }

  Future<void> loadPhoneNumber() async {
    SharedPreferences prefs = await SharedPreferences.getInstance();
    setState(() {
      phoneNumber = prefs.getString('phone') ?? "None";
    });
  }

  void onPhone(String phoneNumber) {}

  // Platform messages are asynchronous, so we initialize in an async method.
  Future<void> initPlatformState() async {
    // Platform messages may fail, so we use a try/catch PlatformException.
    // If the widget was removed from the tree while the asynchronous platform
    // message was in flight, we want to discard the reply rather than calling
    // setState to update our non-existent appearance.

    final bool? resultPermission =
        await phoneState.requestPhoneStatePermissions;
    final bool? resultRollCallScreen =
        await phoneState.requestRollCallScreen;

    if (resultPermission != null &&
        resultPermission &&
        resultRollCallScreen != null &&
        resultRollCallScreen) {
      phoneState.listenIncomingSms(
          onNewPhoneState: onPhone, onBackgroundPhoneStateHandle: onPhoneBg);
    }

    if (!mounted) return;
  }

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      home: Scaffold(
        appBar: AppBar(
          title: const Text('Plugin example app'),
        ),
        body: Center(
          child: Text('Running on: $phoneNumber\n'),
        ),
      ),
    );
  }
}
