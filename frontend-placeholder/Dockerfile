FROM nginx:alpine

# Устанавливаем необходимые пакеты
RUN apk add --no-cache git docker-cli docker-compose fcgiwrap

# Копируем статику фронтенда
COPY ./static /usr/share/nginx/html

# Копируем скрипт вебхука
COPY github-webhook.sh /usr/share/nginx/html/github-webhook.sh
RUN chmod +x /usr/share/nginx/html/github-webhook.sh

# Копируем конфиг Nginx
COPY nginx.conf /etc/nginx/nginx.conf

# Создаём директорию для сокета fcgiwrap
RUN mkdir -p /var/run/fcgiwrap

# Копируем скрипт для запуска fcgiwrap и Nginx
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Указываем точку входа
ENTRYPOINT ["/entrypoint.sh"]
