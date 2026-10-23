<?php
/** Settings → AlcorCut Pro website: social links, beta link, Google Play link, copyright name, pointer. */
if (!defined('ABSPATH')) exit;

add_action('admin_menu', function () {
    add_options_page('AlcorCut Pro website', 'AlcorCut Pro website', 'manage_options', 'alcorcut-site', 'acs_settings_page');
});

add_action('admin_init', function () {
    register_setting('acs', 'acs_settings', [
        'type' => 'array',
        'sanitize_callback' => function ($in) {
            $out = [];
            foreach (['facebook', 'youtube', 'instagram', 'tiktok', 'beta_url', 'play_url'] as $k) $out[$k] = esc_url_raw(trim($in[$k] ?? ''));
            $out['holder'] = sanitize_text_field($in['holder'] ?? 'AlcorCut Pro');
            $out['pointer'] = empty($in['pointer']) ? '0' : '1';
            return $out;
        },
    ]);
    if (isset($_POST['acs_create']) && check_admin_referer('acs_create') && current_user_can('manage_options')) {
        acs_create_pages(!empty($_POST['acs_replace']));
        wp_safe_redirect(admin_url('options-general.php?page=alcorcut-site&created=1'));
        exit;
    }
});

function acs_settings_page() {
    $o = acs_opt();
    $f = function ($k, $label, $help = '') use ($o) {
        printf('<tr><th scope="row"><label for="acs_%1$s">%2$s</label></th><td><input class="regular-text" type="url" id="acs_%1$s" name="acs_settings[%1$s]" value="%3$s" placeholder="https://">%4$s</td></tr>',
            esc_attr($k), esc_html($label), esc_attr($o[$k]), $help ? '<p class="description">' . esc_html($help) . '</p>' : '');
    };
    echo '<div class="wrap"><h1>AlcorCut Pro website</h1>';
    if (!empty($_GET['created'])) echo '<div class="notice notice-success"><p>The pages are ready — see the list below.</p></div>';
    echo '<form method="post" action="options.php">';
    settings_fields('acs');
    echo '<h2>Links</h2><p>Leave a link empty for now: the icon or button still shows, and works as soon as you add the link here.</p><table class="form-table">';
    $f('facebook', 'Facebook page');
    $f('youtube', 'YouTube channel');
    $f('instagram', 'Instagram');
    $f('tiktok', 'TikTok');
    $f('beta_url', 'Apply for beta testing', 'Where the beta button leads. Empty = your Facebook page.');
    $f('play_url', 'Google Play listing', 'Empty = the button says “Coming soon to Google Play”. Add the link when the app is live.');
    printf('<tr><th scope="row"><label for="acs_holder">Copyright name</label></th><td><input class="regular-text" id="acs_holder" name="acs_settings[holder]" value="%s"><p class="description">Footer: © (current year, updated automatically) followed by this name.</p></td></tr>', esc_attr($o['holder']));
    printf('<tr><th scope="row">Glowing pointer</th><td><label><input type="checkbox" name="acs_settings[pointer]" value="1" %s> The app\'s glowing mouse pointer on the website (computers with a mouse only)</label></td></tr>', checked($o['pointer'], '1', false));
    echo '</table>';
    submit_button();
    echo '</form><hr><h2>Pages</h2><ul>';
    $ids = (array) get_option('acs_pages', []);
    foreach (acs_page_defs() as $key => $def) {
        $id = $ids[$key] ?? 0;
        if ($id && get_post_status($id)) printf('<li><strong>%s</strong> — <a href="%s">view</a> · <a href="%s">edit with Elementor</a></li>', esc_html($def['title']), esc_url(get_permalink($id)), esc_url(admin_url('post.php?post=' . $id . '&action=elementor')));
        else printf('<li><strong>%s</strong> — not created yet</li>', esc_html($def['title']));
    }
    echo '</ul><form method="post">';
    wp_nonce_field('acs_create');
    echo '<p><label><input type="checkbox" name="acs_replace" value="1"> Replace the existing pages with fresh copies (your changes in Elementor on them are lost)</label></p>';
    echo '<p><button class="button button-secondary" name="acs_create" value="1">Create the pages</button></p></form></div>';
}
