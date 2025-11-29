import ws from 'k6/ws';
import { check, sleep } from 'k6';
import { Counter, Rate } from 'k6/metrics';

// Custom Metrics
const createdRate = new Rate('room_created_rate');
const selectedRate = new Rate('card_selected_rate');
const roomReadRate = new Rate('room_read_rate');
const errorRate = new Rate('error_rate');

// Configuration
const BASE_URL = 'ws://localhost:8080/ws/games';
const USERS = 1000; // Number of concurrent users
const DURATION = '120s'; // Test duration

export const options = {
  scenarios: {
    rdbms_test: {
      executor: 'constant-vus',
      vus: USERS,
      duration: DURATION,
      exec: 'rdbms',
    },
    global_cache_test: {
      executor: 'constant-vus',
      vus: USERS,
      duration: DURATION,
      exec: 'globalCache',
      startTime: '125s', // Run sequentially
    },
    local_cache_test: {
      executor: 'constant-vus',
      vus: USERS,
      duration: DURATION,
      exec: 'localCache',
      startTime: '250s', // Run sequentially
    },
  },
};

// Helper function to generate random string
function randomString(length) {
  const charset = 'abcdefghijklmnopqrstuvwxyz0123456789';
  let res = '';
  while (length--) res += charset[Math.floor(Math.random() * charset.length)];
  return res;
}

function runTest(strategy) {
  const roomId = `room-${randomString(5)}`;
  const userId = `user-${randomString(5)}`;

  const url = BASE_URL;
  const params = { tags: { my_tag: 'hello' } };

  const res = ws.connect(url, params, function (socket) {
    socket.on('open', function open() {
      // 1. Create Room
      socket.send(JSON.stringify({
        command: 'CREATE_ROOM',
        strategy: strategy,
        roomId: roomId
      }));

      // 2. Select Card (Action)
      sleep(3);

      for (let i = 0; i < 10; i++) {
        socket.send(JSON.stringify({
          command: 'SELECT_CARD',
          strategy: strategy,
          roomId: roomId,
          userId: userId,
          cardValue: 'Ace'
        }));
        sleep(0.1);
      }



      // Close after operations
      sleep(5);
      socket.close();
    });

    socket.on('message', function (message) {
      try {
        const msg = JSON.parse(message);

        if (msg.status === 'CREATED') {
          createdRate.add(1);
        } else if (msg.status === 'SELECTED') {
          selectedRate.add(1);
        } else if (msg.roomId && !msg.status) {
          // Assuming GET_ROOM returns the room object which has roomId but no status field in root
          roomReadRate.add(1);
        } else if (msg.error) {
          errorRate.add(1);
          console.error(`Error from server: ${msg.error}`);
        }
      } catch (e) {
        console.error(`Error parsing message: ${message}`);
        errorRate.add(1);
      }
    });

    socket.on('close', function () {
      // console.log('disconnected');
    });

    socket.on('error', function (e) {
      if (e.error() != 'websocket: close sent') {
        console.log('An unexpected error occurred: ', e.error());
        errorRate.add(1);
      }
    });
  });

  check(res, { 'connected successfully': (r) => r && r.status === 101 });
}

export function rdbms() {
  runTest('rdbms');
}

export function globalCache() {
  runTest('global');
}

export function localCache() {
  runTest('local');
}
