if (!window.native) {
	window.native = {
		getPairedDevices: function() {
			return JSON.stringify(['Dummy']);
		}
	}
}

var connectedDeviceName = null;

function log(message) {
    var wrap = $('#log');

    wrap.append('<div>' + message + '</div>');

    wrap.prop(
        'scrollTop',
        wrap.prop('scrollHeight')
    );
}

function bootstrap() {
    log('Bootstrapping');

    setupControls();
    setupMainMenu();
    setupConsole();
    updateDevices();

    //$(document.body).addClass('show-log');
}

function setupControls() {
    var actions = {
        ledOn: function() {
            native.sendMessage('<on>');
        },
        ledOff: function() {
            native.sendMessage('<off>');
        }
    };

    $('.action-btn').each(function() {
       $(this).click(function() {
           var action = $(this).data('action');

           if (typeof(actions[action]) === 'function') {
               actions[action].call(actions[action]);
           }
       })
    });
}

function setupMainMenu() {
    $('#main-menu > LI').click(function() {
        var viewName = $(this).data('view');

        showView(viewName);
    });
}

function setupConsole() {
    $('#user-message').click(function() {
        if ($(this).text() === 'send message') {
            $(this).text('').removeClass('placeholder');
        }
    });

    $('#user-message').blur(function() {
        if ($(this).text() === '') {
            $(this).text('send message').addClass('placeholder');
        }
    });

    $('#send-message-btn').click(function() {
        var message =  $('#user-message').text();

        if (message.length === 0) {
            return;
        }

        native.sendMessage(message);
    });
}

function showView(name) {
    $('.view').hide();
    $('.view[data-name=' + name + ']').show();

    $('#main-menu > LI').removeClass('active');
    $('#main-menu > LI[data-view=' + name + ']').addClass('active');
}

function updateDevices() {
    var wrap = $('#paired-devices-list'),
        i;

    wrap.empty();

    var devices = JSON.parse(native.getPairedDevices());

    log('Paired: ' + JSON.stringify(devices));

    for (i = 0; i < devices.length; i++) {
        wrap.append('<li class="device-item' + (devices[i] === connectedDeviceName ? ' connected' : '') + '" data-name="' + devices[i] + '">' + devices[i] + '' + (devices[i] === connectedDeviceName ? ' (connected)' : '') + '</li>');
    }

    $('.device-item').click(function() {
        var name = $(this).data('name');

        native.connectDevice(name);
    });
}

function showConsole(deviceName) {
    showView('console');
}

function appendConsole(type, message) {
    var wrap = $('#message-list');

    wrap.append('<li class="' + type + '">' + message.replace('<', '&lt;').replace('>', '&gt;') + '</li>');

    wrap.prop(
        'scrollTop',
        wrap.prop('scrollHeight')
    );
}

function onBluetoothReady() {
    log('Bluetooth ready');

    updateDevices();
    showView('device-choice');
}

function onBluetoothConnected(deviceName) {
    log('Bluetooth connected: ' + deviceName);

	connectedDeviceName = deviceName;

	updateDevices();
	showView('device-choice');
}

function onBluetoothStateChanged(newState) {
    log('Bluetooth state changed to: ' + newState);

	$('#state').html(newState);
}

function onBluetoothMessageReceived(message) {
    log('Bluetooth message received: ' + message);

    appendConsole('rx', '< ' + message);
}

function onBluetoothMessageSent(message) {
    log('Bluetooth message sent: ' + message);

    appendConsole('tx', '> ' + message);
}

function onBluetoothConnectionFailed() {
    log('Bluetooth connection failed');

    updateDevices();
    showView('device-choice');
}

function onBluetoothConnectionLost() {
    log('Bluetooth connection lost');

    updateDevices();
    showView('device-choice');
}

$(document).ready(function() {
    bootstrap();
});