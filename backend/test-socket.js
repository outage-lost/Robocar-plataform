const { io } = require('socket.io-client');

const PORT = process.env.PORT || '3000';
const HOST = process.env.TEST_HOST || `http://localhost:${PORT}`;
const ADMIN_TOKEN = process.env.TOKEN_ADMIN || 'admin123';
const CAMERA_TOKEN = process.env.TOKEN_CAMERA || 'camera123';

// Test admin connection
console.log('🧪 Probando conexión admin...');
const adminSocket = io(`${HOST}/admin`, {
    auth: {
        token: ADMIN_TOKEN
    },
    reconnection: true,
    reconnectionDelay: 1000,
    reconnectionDelayMax: 5000,
    reconnectionAttempts: 5
});

adminSocket.on('connect', () => {
    console.log('✅ Admin conectado!');
    adminSocket.disconnect();
});

adminSocket.on('connect_error', (error) => {
    console.log('❌ Error de conexión admin:', error.message);
    adminSocket.disconnect();
});

adminSocket.on('disconnect', () => {
    console.log('Admin desconectado');

    // Test camera connection
    setTimeout(() => {
        console.log('\n🧪 Probando conexión cámara...');
        const cameraSocket = io(`${HOST}/camera`, {
            auth: {
                token: CAMERA_TOKEN
            }
        });

        cameraSocket.on('connect', () => {
            console.log('✅ Cámara conectada!');
            cameraSocket.disconnect();
        });

        cameraSocket.on('connect_error', (error) => {
            console.log('❌ Error de conexión cámara:', error.message);
            cameraSocket.disconnect();
        });

        cameraSocket.on('disconnect', () => {
            console.log('Cámara desconectada\n✅ Tests completados');
            process.exit(0);
        });

        setTimeout(() => {
            cameraSocket.disconnect();
            process.exit(1);
        }, 3000);
    }, 1000);
});

setTimeout(() => {
    adminSocket.disconnect();
    process.exit(1);
}, 3000);
