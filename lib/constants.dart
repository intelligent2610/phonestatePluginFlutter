part of 'phone_state.dart';

const _FOREGROUND_CHANNEL =
    'plugins.phone_state.com/foreground_phone_state_channel';
const _BACKGROUND_CHANNEL =
    'plugins.phone_state.com/background_phone_state_channel';

const REQUEST_PHONE_STATE_PERMISSION = "requestPhoneStatePermission";
const REQUEST_ROLL_CALL_SCREEN = "requestRollCallScreen";
const INSERT_CUSTOMER_INTO_CONTACT = "insertCustomerIntoContact";

const BACKGROUND_SERVICE_INITIALIZED = "backgroundServiceInitialized";
const HANDLE_BACKGROUND_PHONE_STATE = "handleBackgroundPhoneState";

const ON_PHONE_OFF_HOOK = "onPhoneOffHook";

const String ARG_PHONE_NUMBER = "phone_number";
const String ARG_GROUP_NAME = "group_name";
const String ARG_LAST_NAME = "last_name";
const String ARG_CUSTOMER_NAME = "customer_name";
