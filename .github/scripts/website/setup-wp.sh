#!/usr/bin/env bash
# Fresh WordPress + Elementor + the AlcorCut plugin at http://127.0.0.1:8080 (for tests and the GitHub Pages preview).
set -e
sudo systemctl start mysql.service
curl -fsSL -o /tmp/wp-cli.phar https://raw.githubusercontent.com/wp-cli/builds/gh-pages/phar/wp-cli.phar && sudo mv /tmp/wp-cli.phar /usr/local/bin/wp-cli.phar
printf '#!/bin/sh\nexec php -d display_errors=0 -d error_reporting=0 /usr/local/bin/wp-cli.phar "$@"\n' | sudo tee /usr/local/bin/wp >/dev/null && sudo chmod +x /usr/local/bin/wp
mkdir -p ~/site && cd ~/site
wp core download --quiet
wp config create --dbname=wp --dbuser=root --dbpass=root --dbhost=127.0.0.1 --quiet
wp config set WP_DEBUG true --raw; wp config set WP_DEBUG_LOG true --raw; wp config set WP_DEBUG_DISPLAY false --raw
wp db create
wp core install --url=http://127.0.0.1:8080 --title="AlcorCut Pro" --admin_user=admin --admin_password=admin --admin_email=test@example.com --skip-email
wp rewrite structure '/%postname%/' --quiet
wp plugin install elementor --activate
cp -r "$GITHUB_WORKSPACE/Website/alcorcut-site" wp-content/plugins/
wp plugin activate alcorcut-site
nohup wp server --host=127.0.0.1 --port=8080 --path=$HOME/site > /tmp/server.log 2>&1 &
sleep 4
