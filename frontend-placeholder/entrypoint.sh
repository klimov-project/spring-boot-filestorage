#!/bin/sh

# Проверяем, что Docker Socket существует
if [ ! -S /var/run/docker.sock ]; then
  echo "Docker Socket не найден!"
  exit 1
fi

# Запускаем fcgiwrap в фоновом режиме
fcgiwrap -s unix:/var/run/fcgiwrap.socket &

# Ждём, пока сокет появится
while [ ! -e /var/run/fcgiwrap.socket ]; do
  sleep 1
done

# Запускаем Nginx
nginx -g "daemon off;"
