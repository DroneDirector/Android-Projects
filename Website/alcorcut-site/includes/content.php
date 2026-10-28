<?php
/** All the website's starting text and pictures. Everything here can be changed later in Elementor. */
if (!defined('ABSPATH')) exit;

function acs_img($file) { return ['url' => ACS_URL . 'assets/img/' . $file, 'id' => '']; }

function acs_content() {
    return [
        'hero' => [
            'eyebrow'  => 'For Android phones & tablets',
            'title'    => 'Truly professional photo & video. <em>On Android.</em>',
            'text'     => 'AlcorCut Pro is a pro camera, a RAW photo editor and a multi-track video editor in one app — with hundreds of features Android has been missing.',
            'film'     => 'Watch the film',
            'badges'   => "Android only\nRAW development\n4K HDR export\nOn-device AI",
            'shot_a'   => acs_img('shot-photo.jpg'),
            'shot_b'   => acs_img('shot-capture.jpg'),
            'shot_c'   => acs_img('shot-video.jpg'),
            // More screens: they take turns in the same carousel after the first three.
            'more'     => [
                ['image' => acs_img('shot-video-timeline.jpg'), 'caption' => 'Video editor · Timeline and colour'],
                ['image' => acs_img('shot-photo-adaptive.jpg'), 'caption' => 'Photo editor · Adaptive presets (AI)'],
                ['image' => acs_img('shot-photo-color.jpg'), 'caption' => 'Photo editor · Colour'],
                ['image' => acs_img('shot-photo-heal.jpg'), 'caption' => 'Photo editor · Heal and dust spots'],
                ['image' => acs_img('shot-photo-presets.jpg'), 'caption' => 'Photo editor · Presets'],
                ['image' => acs_img('shot-start.jpg'), 'caption' => 'Start screen'],
                ['image' => acs_img('shot-editors.jpg'), 'caption' => 'Photo and video editor'],
            ],
        ],
        'stats' => [
            ['num' => '300+', 'label' => 'pro features'],
            ['num' => '3', 'label' => 'apps in one: Capture, Photo, Video'],
            ['num' => '4K', 'label' => 'HDR video export'],
            ['num' => 'RAW', 'label' => 'DNG, Sony, Canon, Nikon, Fujifilm, DJI…'],
        ],
        'android' => [
            'eyebrow' => 'Android only',
            'title'   => 'Built for Android. <em>Finally.</em>',
            'text'    => '<p>iPhone and iPad users have plenty of great pro apps for photo and video. On Android, the choice has been thin — and most of what is there stays basic.</p><p><strong>AlcorCut Pro changes that.</strong> It turns “basic” into truly professional: a pro camera, a RAW photo editor and a multi-track video editor in one app, with hundreds of features. Made for Android phones and tablets, from Android 8.0 up.</p>',
            'basic'   => 'basic',
            'pro'     => 'professional',
        ],
        'video' => [
            'eyebrow' => 'See it in action',
            'title'   => 'AlcorCut Pro at work',
            'text'    => 'A short film of the camera, the photo editor and the video editor.',
            'vimeo'   => '',
            'soon'    => 'Video coming soon',
        ],
        'trio' => [
            ['key' => 'capture', 'title' => 'Capture', 'text' => 'A pro camera with full manual control and cinema-grade monitoring.', 'link' => '#capture'],
            ['key' => 'photo', 'title' => 'Photo editor', 'text' => 'Real RAW development, AI masks and print-grade output.', 'link' => '#photo'],
            ['key' => 'video', 'title' => 'Video editor', 'text' => 'A multi-track timeline with grading, keyframes and pro delivery.', 'link' => '#video'],
        ],
        'pro' => [
            'capture' => [
                'anchor'  => 'capture',
                'eyebrow' => 'Capture',
                'title'   => 'Why Capture is <em>pro</em>',
                'text'    => 'The tools of a cinema camera, in your pocket.',
                'reasons' => [
                    ['title' => 'Full manual control', 'text' => 'ISO, shutter, focus, white balance and EV — with auto, shutter priority, ISO priority and manual exposure modes.'],
                    ['title' => 'Cinema monitoring', 'text' => 'Zebras, false colour, focus peaking, waveform, RGB parade, vectorscope, a focus magnifier and your own .cube monitoring LUT.'],
                    ['title' => 'Real cinema settings', 'text' => 'Cinema frame shapes (2.39, 1.85, 2.76…), shutter angle with one-tap 180°, NTSC rates, anamorphic desqueeze and A / B pulls for focus, zoom and exposure.'],
                    ['title' => 'Pro sound', 'text' => '24-bit WAV, manual gain with limiter, headphone monitoring, stereo level meters and a wind filter.'],
                    ['title' => 'Built for the set', 'text' => 'Slate & shot log (CSV), custom set-ups C1–C3, remote monitor, clean feed and a Wear OS watch remote.'],
                ],
                'shot'    => acs_img('shot-capture.jpg'),
                'more'    => 'capture',
                'flip'    => '',
            ],
            'photo' => [
                'anchor'  => 'photo',
                'eyebrow' => 'Photo editor',
                'title'   => 'Why the Photo editor is <em>pro</em>',
                'text'    => 'From sensor data to print — without leaving your phone.',
                'reasons' => [
                    ['title' => 'Real RAW development', 'text' => 'Reads the sensor data itself — DNG and camera RAW from Sony, Canon, Nikon, Fujifilm, DJI and more — with RAW looks measured from each maker.'],
                    ['title' => 'Pro colour tools', 'text' => 'Tone curve, colour wheels, HSL mixer, point colour, calibration, and a targeted-adjustment tool you drag right on the photo.'],
                    ['title' => 'AI masks', 'text' => 'Sky, people (skin, eyes, hair, clothes…), objects and depth — combined with add, subtract and intersect.'],
                    ['title' => 'Retouching', 'text' => 'Healing, automatic dust-spot finding, AI object removal, liquify and frequency separation.'],
                    ['title' => 'Print-grade output', 'text' => 'TIFF, colour spaces, output sharpening, soft proofing with gamut warning, Ultra HDR, export presets and .xmp presets for other RAW editors.'],
                ],
                'shot'    => acs_img('shot-photo.jpg'),
                'more'    => 'photo',
                'flip'    => 'yes',
            ],
            'video' => [
                'anchor'  => 'video',
                'eyebrow' => 'Video editor',
                'title'   => 'Why the Video editor is <em>pro</em>',
                'text'    => 'A real editing suite — not a clip trimmer.',
                'reasons' => [
                    ['title' => 'A real multi-track timeline', 'text' => 'Unlimited video layers, keyframes with Bézier curves, motion tracking, nesting, J- and L-cuts, and slip, roll and slide trims.'],
                    ['title' => 'Colour grading', 'text' => 'Colour wheels, curves, HSL, LUTs, masks and film & lens effects — with scopes.'],
                    ['title' => 'Pro workflow', 'text' => 'Proxies, multicam synced by sound, a render cache, project archives and version history.'],
                    ['title' => 'Pro audio', 'text' => 'Parametric EQ with de-esser and live spectrum, loudness normalisation (−14 / −16 / −23 LUFS), voiceover and beat markers.'],
                    ['title' => 'Pro delivery', 'text' => 'Background export and render queue, 4K with HDR kept, smart render, review copies with timecode, EDL / FCPXML / OpenTimelineIO and .srt / .vtt subtitles.'],
                ],
                'shot'    => acs_img('shot-video.jpg'),
                'more'    => 'video',
                'flip'    => '',
            ],
        ],
        'gallery' => [
            'eyebrow' => 'Inside the app',
            'title'   => 'Made to look as good as <em>your work</em>',
            'shots'   => [
                ['image' => acs_img('shot-capture.jpg'), 'caption' => 'Capture · Pro video'],
                ['image' => acs_img('shot-photo.jpg'), 'caption' => 'Photo editor · Presets'],
                ['image' => acs_img('shot-grading.jpg'), 'caption' => 'Photo editor · Colour grading'],
                ['image' => acs_img('shot-video.jpg'), 'caption' => 'Video editor · Colour wheels'],
            ],
        ],
        'cards' => [
            'eyebrow' => 'Built the pro way',
            'title'   => 'Everything a pro <em>expects</em>',
            'items'   => [
                ['icon' => 'chip', 'title' => 'AI on your phone', 'text' => 'Noise reduction, masks, object removal and captions run on the device. Cloud AI is optional, with your own fal.ai key.'],
                ['icon' => 'lock', 'title' => 'Your media stays yours', 'text' => 'Photos and videos stay on your phone unless you choose Cloud AI or live streaming.'],
                ['icon' => 'flow', 'title' => 'Fits your workflow', 'text' => 'EDL, FCPXML and OpenTimelineIO for desktop editors, .xmp presets, .cube LUTs, backups and project archives.'],
                ['icon' => 'watch', 'title' => 'Watch remote', 'text' => 'Live picture, record, shutter and zoom from a Wear OS watch.'],
                ['icon' => 'screens', 'title' => 'Phones, tablets & desktop mode', 'text' => 'Layouts for every screen and rotation — plus external monitors and Samsung DeX with full mouse support.'],
                ['icon' => 'globe', 'title' => 'Speaks your language', 'text' => 'In English, Danish and Faroese.'],
            ],
        ],
        'beta' => [
            'title'  => 'Want to try it first?',
            'text'   => 'Join the beta and help shape AlcorCut Pro.',
            'button' => 'Apply for beta testing',
            'reward' => 'Your reward: the finished app, free, when it’s released.',
        ],
        'share' => ['title' => 'Share AlcorCut Pro'],
        'footer' => [
            'tagline' => 'Pro camera · RAW photo editor · Video editor — for Android.',
            'note'    => 'Android is a trademark of Google LLC. Other names belong to their owners.',
        ],
        'features' => [
            'capture' => ['title' => 'Capture — all features', 'text' => 'Everything the camera can do, from manual control to cinema monitoring.'],
            'photo'   => ['title' => 'Photo editor — all features', 'text' => 'Every tool in the photo editor, from RAW development to print.'],
            'video'   => ['title' => 'Video editor — all features', 'text' => 'Every tool in the video editor, from the timeline to delivery.'],
        ],
    ];
}
