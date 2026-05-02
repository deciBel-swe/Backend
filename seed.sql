-- =====================================
-- DeciBel Database Seed Script
-- =====================================
-- Run with: psql -U DeciBel -d database_name -f seed.sql

-- Clear existing data (in reverse order of dependencies)
TRUNCATE TABLE
    playlist_tracks,
    playlist_genres,
    playlist_likes,
    playlist_reposts,
    playlist_tokens,
    track_likes,
    track_tags,
    track_tokens,
    reposts,
    comments,
    notifications,
    notification_preferences,
    follows,
    blocks,
    reports,
    fcm_tokens,
    sessions,
    social_links,
    subscriptions,
    user_favorite_genres,
    user_profile_tokens,
    pending_email_changes,
    tokens,
    listening_history,
    playlists,
    tracks,
    tags,
    auth_identities,
    users,
    admins
RESTART IDENTITY CASCADE;

-- =====================================
-- USERS TABLE SAFETY FIX
-- =====================================

ALTER TABLE users
    ALTER COLUMN free_tracks_left SET DEFAULT 5;

ALTER TABLE users
    ALTER COLUMN free_tracks_left SET NOT NULL;

-- =====================================
-- USERS
-- =====================================
INSERT INTO users (id, username, display_name, bio, location, avatar_url, cover_photo_url, tier, is_private, show_history, follower_count, following_count, track_count, free_tracks_left, created_at, updated_at) VALUES
                                                                                                                                                                                                                       (1, 'djmike',      'DJ Mike',      'Electronic music producer from NYC',        'New York, USA',    'https://i.pravatar.cc/300?u=djmike',      NULL, 'FREE', false, true, 150, 75,  5,   5,   NOW() - INTERVAL '6 months', NOW()),
                                                                                                                                                                                                                       (2, 'sarahbeats',  'Sarah Beats',  'Lo-fi hip hop beats to relax/study to',     'Los Angeles, USA', 'https://i.pravatar.cc/300?u=sarahbeats',  NULL, 'PRO',  false, true, 320, 120, 12,  999, NOW() - INTERVAL '1 year',   NOW()),
                                                                                                                                                                                                                       (3, 'rocknroll99', 'Rock N Roll',  'Classic rock enthusiast and cover artist',  'London, UK',       'https://i.pravatar.cc/300?u=rocknroll99', NULL, 'FREE', false, true, 89,  45,  3,   5,   NOW() - INTERVAL '3 months', NOW()),
                                                                                                                                                                                                                       (4, 'jazzlover',   'Jazz Lover',   'Smooth jazz and saxophone covers',          'Paris, France',    'https://i.pravatar.cc/300?u=jazzlover',   NULL, 'PRO',  false, true, 210, 98,  8,   999, NOW() - INTERVAL '8 months', NOW()),
                                                                                                                                                                                                                       (5, 'indieartist', 'Indie Artist', 'Independent musician exploring new sounds', 'Berlin, Germany',  'https://i.pravatar.cc/300?u=indieartist', NULL, 'FREE', true,  true, 56,  67,  2,   5,   NOW() - INTERVAL '2 months', NOW());

-- =====================================
-- AUTH IDENTITIES
-- Password for all users: Password123!
-- Hash: $2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC
-- =====================================
INSERT INTO auth_identities (auth_id, user_id, email, password_hash, provider, provider_user_id, type, email_verified) VALUES
                                                                                                                           (1, 1, 'djmike@example.com', '$2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC', 'LOCAL', NULL, 'PASSWORD', true),
                                                                                                                           (2, 2, 'sarah@example.com',  '$2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC', 'LOCAL', NULL, 'PASSWORD', true),
                                                                                                                           (3, 3, 'rock99@example.com', '$2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC', 'LOCAL', NULL, 'PASSWORD', true),
                                                                                                                           (4, 4, 'jazz@example.com',   '$2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC', 'LOCAL', NULL, 'PASSWORD', true),
                                                                                                                           (5, 5, 'indie@example.com',  '$2b$10$EEYtHTrOd7XaZQY8.HA3auKyllDiPS9XA4YfMH0S8njyYXVm0tTgC', 'LOCAL', NULL, 'PASSWORD', true);

-- =====================================
-- ADMINS
-- Password: Admin123!
-- Hash: $2b$10$CnLDVP0cmIFpUzLccXAVz.EeP/pSKJzAp4TjkU.hlds7wm8xORJhm
-- =====================================
INSERT INTO admins (id, username, email, password, avatar_url, device_info) VALUES
    (1, 'admin', 'admin@decibel.com', '$2b$10$CnLDVP0cmIFpUzLccXAVz.EeP/pSKJzAp4TjkU.hlds7wm8xORJhm', 'https://i.pravatar.cc/300?u=admin', 'Chrome on Windows');

-- =====================================
-- NOTIFICATION PREFERENCES
-- =====================================
INSERT INTO notification_preferences (id, user_id, notify_on_follow, notify_on_like, notify_on_comment, notify_on_repost, notify_ondm) VALUES
                                                                                                                                           (1, 1, true,  true,  true,  true,  true),
                                                                                                                                           (2, 2, true,  true,  true,  true,  false),
                                                                                                                                           (3, 3, true,  false, true,  true,  true),
                                                                                                                                           (4, 4, true,  true,  true,  false, true),
                                                                                                                                           (5, 5, false, false, false, false, false);

-- =====================================
-- FOLLOWS
-- =====================================
INSERT INTO follows (id, follower_id, following_id, followed_at) VALUES
                                                                     (1,  1, 2, NOW() - INTERVAL '5 days'),
                                                                     (2,  1, 4, NOW() - INTERVAL '10 days'),
                                                                     (3,  2, 1, NOW() - INTERVAL '3 days'),
                                                                     (4,  2, 3, NOW() - INTERVAL '7 days'),
                                                                     (5,  2, 4, NOW() - INTERVAL '2 days'),
                                                                     (6,  3, 1, NOW() - INTERVAL '15 days'),
                                                                     (7,  3, 2, NOW() - INTERVAL '8 days'),
                                                                     (8,  4, 2, NOW() - INTERVAL '12 days'),
                                                                     (9,  5, 2, NOW() - INTERVAL '1 day'),
                                                                     (10, 5, 4, NOW() - INTERVAL '4 days');

-- =====================================
-- SOCIAL LINKS
-- =====================================
INSERT INTO social_links (id, user_id, platform, url) VALUES
                                                          (1, 1, 'TWITTER',   'https://twitter.com/djmike'),
                                                          (2, 1, 'INSTAGRAM', 'https://instagram.com/djmike'),
                                                          (3, 2, 'TWITTER',   'https://twitter.com/sarahbeats'),
                                                          (4, 2, 'WEBSITE',   'https://sarahbeats.com'),
                                                          (5, 4, 'INSTAGRAM', 'https://instagram.com/jazzlover');

-- =====================================
-- TAGS
-- =====================================
INSERT INTO tags (tag_id, title) VALUES
                                     (1,  'electronic'),
                                     (2,  'chill'),
                                     (3,  'study'),
                                     (4,  'rock'),
                                     (5,  'jazz'),
                                     (6,  'indie'),
                                     (7,  'ambient'),
                                     (8,  'upbeat'),
                                     (9,  'relaxing'),
                                     (10, 'energetic');

-- =====================================
-- TRACKS
-- =====================================
INSERT INTO tracks (id, uploader_id, title, description, genre, slug, duration_seconds, release_date, published, published_at, upload_date, visibility, state, like_count, repost_count, play_count, play_through_rate, track_url, cover_url, waveform_url) VALUES
                                                                                                                                                                                                                                                                (1,  1, 'Midnight Dreams',         'An electronic track perfect for late nights', 'Electronic', 'midnight-dreams',     245, '2024-01-15', true,  NOW() - INTERVAL '5 months', NOW() - INTERVAL '5 months', 'PUBLIC',  'FINISHED',  45,  12, 1250, 0.75, 'https://example.com/tracks/midnight-dreams.mp3', 'https://picsum.photos/seed/track1/400/400', 'https://example.com/waveforms/1.json'),
                                                                                                                                                                                                                                                                (2,  1, 'Electric Pulse',          'High energy EDM',                             'Electronic', 'electric-pulse',      198, '2024-02-20', true,  NOW() - INTERVAL '4 months', NOW() - INTERVAL '4 months', 'PUBLIC',  'FINISHED',  67,  18, 2100, 0.82, 'https://example.com/tracks/electric-pulse.mp3',  'https://picsum.photos/seed/track2/400/400', 'https://example.com/waveforms/2.json'),
                                                                                                                                                                                                                                                                (3,  2, 'Lo-fi Study Session',     'Chill beats for studying',                    'Lo-fi',      'lofi-study-session',  180, '2024-03-10', true,  NOW() - INTERVAL '3 months', NOW() - INTERVAL '3 months', 'PUBLIC',  'FINISHED',  156, 45, 5600, 0.88, 'https://example.com/tracks/lofi-study.mp3',      'https://picsum.photos/seed/track3/400/400', 'https://example.com/waveforms/3.json'),
                                                                                                                                                                                                                                                                (4,  2, 'Rainy Day Vibes',         'Perfect for rainy afternoons',                'Lo-fi',      'rainy-day-vibes',     210, '2024-04-05', true,  NOW() - INTERVAL '2 months', NOW() - INTERVAL '2 months', 'PUBLIC',  'FINISHED',  89,  23, 3400, 0.79, 'https://example.com/tracks/rainy-day.mp3',       'https://picsum.photos/seed/track4/400/400', 'https://example.com/waveforms/4.json'),
                                                                                                                                                                                                                                                                (5,  3, 'Rock Anthem Cover',       'My take on a classic rock anthem',            'Rock',       'rock-anthem-cover',   265, '2024-05-12', true,  NOW() - INTERVAL '1 month',  NOW() - INTERVAL '1 month',  'PUBLIC',  'FINISHED',  34,  8,  890,  0.71, 'https://example.com/tracks/rock-anthem.mp3',     'https://picsum.photos/seed/track5/400/400', 'https://example.com/waveforms/5.json'),
                                                                                                                                                                                                                                                                (6,  4, 'Smooth Jazz Evening',     'Piano melodies for relaxation',               'Jazz',       'smooth-jazz-evening', 320, '2024-01-30', true,  NOW() - INTERVAL '4 months', NOW() - INTERVAL '4 months', 'PUBLIC',  'FINISHED',  112, 31, 4200, 0.85, 'https://example.com/tracks/smooth-jazz.mp3',     'https://picsum.photos/seed/track6/400/400', 'https://example.com/waveforms/6.json'),
                                                                                                                                                                                                                                                                (7,  4, 'Blue Note Improvisation', 'Live jazz improvisation',                     'Jazz',       'blue-note-improv',    420, '2024-03-20', true,  NOW() - INTERVAL '2 months', NOW() - INTERVAL '2 months', 'PUBLIC',  'FINISHED',  78,  19, 2800, 0.68, 'https://example.com/tracks/blue-note.mp3',       'https://picsum.photos/seed/track7/400/400', 'https://example.com/waveforms/7.json'),
                                                                                                                                                                                                                                                                (8,  5, 'Indie Dreams',            'Experimental indie sound',                    'Indie',      'indie-dreams',        195, '2024-06-01', true,  NOW() - INTERVAL '10 days',  NOW() - INTERVAL '10 days',  'PRIVATE', 'FINISHED',  5,   1,  120,  0.65, 'https://example.com/tracks/indie-dreams.mp3',    'https://picsum.photos/seed/track8/400/400', 'https://example.com/waveforms/8.json'),
                                                                                                                                                                                                                                                                (9,  2, 'Sunset Chill',            'Ambient lo-fi for sunset watching',           'Lo-fi',      'sunset-chill',        240, '2024-06-15', true,  NOW() - INTERVAL '5 days',   NOW() - INTERVAL '5 days',   'PUBLIC',  'FINISHED',  201, 56, 6700, 0.91, 'https://example.com/tracks/sunset-chill.mp3',    'https://picsum.photos/seed/track9/400/400', 'https://example.com/waveforms/9.json'),
                                                                                                                                                                                                                                                                (10, 1, 'Work in Progress',        'New track coming soon',                       'Electronic', NULL,                  180, '2024-06-20', false, NULL,                        NOW() - INTERVAL '2 days',   'PRIVATE', 'UPLOADING', 0,   0,  0,    0.0,  NULL,                                             NULL,                                        NULL);

-- =====================================
-- TRACK TAGS
-- =====================================
INSERT INTO track_tags (track_id, tag_id) VALUES
                                              (1, 1), (1, 2), (1, 7),
                                              (2, 1), (2, 8), (2, 10),
                                              (3, 2), (3, 3), (3, 9),
                                              (4, 2), (4, 9), (4, 7),
                                              (5, 4), (5, 10),
                                              (6, 5), (6, 9), (6, 2),
                                              (7, 5), (7, 10),
                                              (8, 6), (8, 7),
                                              (9, 2), (9, 9), (9, 7);

-- =====================================
-- TRACK LIKES
-- =====================================
INSERT INTO track_likes (id, user_id, track_id, liked_at) VALUES
                                                              (1,  2, 1, NOW() - INTERVAL '4 days'),
                                                              (2,  3, 1, NOW() - INTERVAL '3 days'),
                                                              (3,  4, 1, NOW() - INTERVAL '2 days'),
                                                              (4,  1, 3, NOW() - INTERVAL '5 days'),
                                                              (5,  3, 3, NOW() - INTERVAL '4 days'),
                                                              (6,  4, 3, NOW() - INTERVAL '2 days'),
                                                              (7,  5, 3, NOW() - INTERVAL '1 day'),
                                                              (8,  1, 6, NOW() - INTERVAL '3 days'),
                                                              (9,  2, 6, NOW() - INTERVAL '2 days'),
                                                              (10, 3, 9, NOW() - INTERVAL '1 day'),
                                                              (11, 4, 9, NOW() - INTERVAL '1 day'),
                                                              (12, 5, 9, NOW() - INTERVAL '6 hours');

-- =====================================
-- REPOSTS
-- =====================================
INSERT INTO reposts (id, user_id, track_id, reposted_at) VALUES
                                                             (1, 2, 1, NOW() - INTERVAL '3 days'),
                                                             (2, 4, 1, NOW() - INTERVAL '2 days'),
                                                             (3, 1, 3, NOW() - INTERVAL '4 days'),
                                                             (4, 3, 3, NOW() - INTERVAL '2 days'),
                                                             (5, 4, 6, NOW() - INTERVAL '1 day'),
                                                             (6, 2, 9, NOW() - INTERVAL '2 days');

-- =====================================
-- COMMENTS
-- =====================================
INSERT INTO comments (id, user_id, track_id, parent_comment_id, content, timestamp_seconds, created_at) VALUES
                                                                                                            (1, 2, 1, NULL, 'This is amazing! Love the vibe',  45,   NOW() - INTERVAL '3 days'),
                                                                                                            (2, 3, 1, NULL, 'Great production quality',             120,  NOW() - INTERVAL '2 days'),
                                                                                                            (3, 1, 1, 1,    'Thanks so much! Appreciate it',        NULL, NOW() - INTERVAL '2 days'),
                                                                                                            (4, 4, 3, NULL, 'Perfect for studying!',                NULL, NOW() - INTERVAL '5 days'),
                                                                                                            (5, 5, 3, NULL, 'This helped me get through finals',    30,   NOW() - INTERVAL '4 days'),
                                                                                                            (6, 2, 3, 5,    'Glad it helped! Good luck!',           NULL, NOW() - INTERVAL '3 days'),
                                                                                                            (7, 1, 6, NULL, 'Smooth!',                              180,  NOW() - INTERVAL '2 days'),
                                                                                                            (8, 3, 9, NULL, 'On repeat all day',                    60,   NOW() - INTERVAL '1 day');

-- =====================================
-- PLAYLISTS
-- =====================================
INSERT INTO playlists (id, user_id, title, description, slug, type, is_private, is_liked, like_count, repost_count, track_count, total_duration_seconds, cover_art_url, created_at, updated_at) VALUES
                                                                                                                                                                                                    (1, 2, 'Chill Study Beats',   'Lo-fi beats compilation for focused studying', 'chill-study-beats',  'PLAYLIST', false, false, 78,  23, 3, 630, 'https://picsum.photos/seed/playlist1/400/400', NOW() - INTERVAL '2 months', NOW() - INTERVAL '1 day'),
                                                                                                                                                                                                    (2, 1, 'Electronic Vibes',    'Best electronic tracks',                       'electronic-vibes',   'PLAYLIST', false, false, 45,  12, 2, 443, 'https://picsum.photos/seed/playlist2/400/400', NOW() - INTERVAL '3 months', NOW() - INTERVAL '5 days'),
                                                                                                                                                                                                    (3, 4, 'Jazz Collection',     'My favorite jazz pieces',                      'jazz-collection',    'PLAYLIST', false, false, 34,  8,  2, 740, 'https://picsum.photos/seed/playlist3/400/400', NOW() - INTERVAL '1 month',  NOW() - INTERVAL '2 days'),
                                                                                                                                                                                                    (4, 2, 'Lo-fi Greatest Hits', 'Best of lo-fi hip hop',                        'lofi-greatest-hits', 'ALBUM',    false, false, 156, 45, 3, 630, 'https://picsum.photos/seed/playlist4/400/400', NOW() - INTERVAL '4 months', NOW() - INTERVAL '10 days');

-- =====================================
-- PLAYLIST TRACKS
-- =====================================
INSERT INTO playlist_tracks (playlist_id, track_id) VALUES
                                                        (1, 3), (1, 4), (1, 9),
                                                        (2, 1), (2, 2),
                                                        (3, 6), (3, 7),
                                                        (4, 3), (4, 4), (4, 9);

-- =====================================
-- PLAYLIST LIKES
-- =====================================
INSERT INTO playlist_likes (id, user_id, playlist_id, liked_at) VALUES
                                                                    (1, 1, 1, NOW() - INTERVAL '5 days'),
                                                                    (2, 3, 1, NOW() - INTERVAL '3 days'),
                                                                    (3, 4, 1, NOW() - INTERVAL '2 days'),
                                                                    (4, 2, 3, NOW() - INTERVAL '4 days'),
                                                                    (5, 1, 4, NOW() - INTERVAL '1 day');

-- =====================================
-- PLAYLIST REPOSTS
-- =====================================
INSERT INTO playlist_reposts (id, user_id, playlist_id, reposted_at) VALUES
                                                                         (1, 1, 1, NOW() - INTERVAL '4 days'),
                                                                         (2, 3, 1, NOW() - INTERVAL '2 days'),
                                                                         (3, 2, 3, NOW() - INTERVAL '3 days');

-- =====================================
-- PLAYLIST GENRES
-- =====================================
INSERT INTO playlist_genres (playlist_id, genres) VALUES
                                                      (1, 'Lo-fi'),
                                                      (1, 'Chill'),
                                                      (2, 'Electronic'),
                                                      (2, 'EDM'),
                                                      (3, 'Jazz'),
                                                      (4, 'Lo-fi'),
                                                      (4, 'Hip-Hop');

-- =====================================
-- NOTIFICATIONS
-- =====================================
INSERT INTO notifications (id, recipient_id, actor_id, type, resource_type, resource_id, is_read, created_at) VALUES
                                                                                                                  (1, 1, 2, 'FOLLOW',  'USER',  2, true,  NOW() - INTERVAL '5 days'),
                                                                                                                  (2, 1, 2, 'LIKE',    'TRACK', 1, true,  NOW() - INTERVAL '4 days'),
                                                                                                                  (3, 2, 1, 'LIKE',    'TRACK', 3, false, NOW() - INTERVAL '5 days'),
                                                                                                                  (4, 2, 3, 'COMMENT', 'TRACK', 3, true,  NOW() - INTERVAL '5 days'),
                                                                                                                  (5, 1, 4, 'FOLLOW',  'USER',  4, false, NOW() - INTERVAL '2 days'),
                                                                                                                  (6, 4, 1, 'LIKE',    'TRACK', 6, false, NOW() - INTERVAL '3 days');

-- =====================================
-- SUBSCRIPTIONS
-- =====================================
INSERT INTO subscriptions (id, user_id, stripe_customer_id, stripe_subscription_id, plan, status, cancel_at_period_end, current_period_end, created_at, updated_at) VALUES
                                                                                                                                                                        (1, 2, 'cus_test123456', 'sub_test123456', 'PRO', 'ACTIVE', false, EXTRACT(EPOCH FROM (NOW() + INTERVAL '30 days'))::bigint, NOW() - INTERVAL '1 year',   NOW()),
                                                                                                                                                                        (2, 4, 'cus_test789012', 'sub_test789012', 'PRO', 'ACTIVE', false, EXTRACT(EPOCH FROM (NOW() + INTERVAL '25 days'))::bigint, NOW() - INTERVAL '8 months', NOW());

-- =====================================
-- USER FAVORITE GENRES
-- =====================================
INSERT INTO user_favorite_genres (user_id, favorite_genres) VALUES
                                                                (1, 'Electronic'),
                                                                (1, 'House'),
                                                                (2, 'Lo-fi'),
                                                                (2, 'Hip-Hop'),
                                                                (3, 'Rock'),
                                                                (3, 'Alternative'),
                                                                (4, 'Jazz'),
                                                                (4, 'Blues'),
                                                                (5, 'Indie'),
                                                                (5, 'Alternative');

-- =====================================
-- TRACK TOKENS
-- =====================================
INSERT INTO track_tokens (id, track_id, token, is_deleted) VALUES
                                                               (1, 1, 'tk_midnight_dreams_abc123', false),
                                                               (2, 3, 'tk_lofi_study_def456',      false),
                                                               (3, 6, 'tk_smooth_jazz_ghi789',     false);

-- =====================================
-- PLAYLIST TOKENS
-- =====================================
INSERT INTO playlist_tokens (id, playlist_id, token, is_deleted) VALUES
                                                                     (1, 1, 'pl_chill_study_xyz123', false),
                                                                     (2, 4, 'pl_lofi_hits_uvw456',   false);

-- =====================================
-- USER PROFILE TOKENS
-- =====================================
INSERT INTO user_profile_tokens (id, user_id, token, is_deleted) VALUES
                                                                     (1, 1, 'usr_djmike_token123', false),
                                                                     (2, 2, 'usr_sarah_token456',  false);

-- =====================================
-- BLOCKS
-- =====================================
INSERT INTO blocks (id, blocker_id, blocked_id, blocked_at) VALUES
    (1, 3, 5, NOW() - INTERVAL '10 days');

-- =====================================
-- REPORTS
-- =====================================
INSERT INTO reports (id, reporter_id, target_type, target_id, reason, description, status, created_at) VALUES
                                                                                                           (1, 3, 'COMMENT', 5, 'SPAM',                  'Promotional spam in comments', 'RESOLVED',  NOW() - INTERVAL '15 days'),
                                                                                                           (2, 1, 'TRACK',   8, 'INAPPROPRIATE_CONTENT', 'Contains offensive lyrics',    'IN_REVIEW', NOW() - INTERVAL '5 days');

-- =====================================
-- RESET SEQUENCES
-- =====================================
SELECT setval('users_id_seq',                    (SELECT MAX(id)      FROM users));
SELECT setval('auth_identities_auth_id_seq',     (SELECT MAX(auth_id) FROM auth_identities));
SELECT setval('admins_id_seq',                   (SELECT MAX(id)      FROM admins));
SELECT setval('tracks_id_seq',                   (SELECT MAX(id)      FROM tracks));
SELECT setval('tags_tag_id_seq',                 (SELECT MAX(tag_id)  FROM tags));
SELECT setval('playlists_id_seq',                (SELECT MAX(id)      FROM playlists));
SELECT setval('follows_id_seq',                  (SELECT MAX(id)      FROM follows));
SELECT setval('track_likes_id_seq',              (SELECT MAX(id)      FROM track_likes));
SELECT setval('reposts_id_seq',                  (SELECT MAX(id)      FROM reposts));
SELECT setval('comments_id_seq',                 (SELECT MAX(id)      FROM comments));
SELECT setval('playlist_likes_id_seq',           (SELECT MAX(id)      FROM playlist_likes));
SELECT setval('playlist_reposts_id_seq',         (SELECT MAX(id)      FROM playlist_reposts));
SELECT setval('notifications_id_seq',            (SELECT MAX(id)      FROM notifications));
SELECT setval('notification_preferences_id_seq', (SELECT MAX(id)      FROM notification_preferences));
SELECT setval('subscriptions_id_seq',            (SELECT MAX(id)      FROM subscriptions));
SELECT setval('social_links_id_seq',             (SELECT MAX(id)      FROM social_links));
SELECT setval('track_tokens_id_seq',             (SELECT MAX(id)      FROM track_tokens));
SELECT setval('playlist_tokens_id_seq',          (SELECT MAX(id)      FROM playlist_tokens));
SELECT setval('user_profile_tokens_id_seq',      (SELECT MAX(id)      FROM user_profile_tokens));
SELECT setval('blocks_id_seq',                   (SELECT MAX(id)      FROM blocks));
SELECT setval('reports_id_seq',                  (SELECT MAX(id)      FROM reports));

-- =====================================
-- VERIFICATION SUMMARY
-- =====================================
SELECT 'Seed data inserted successfully!' AS status;

SELECT
    (SELECT COUNT(*) FROM users)       AS users,
    (SELECT COUNT(*) FROM tracks)      AS tracks,
    (SELECT COUNT(*) FROM playlists)   AS playlists,
    (SELECT COUNT(*) FROM comments)    AS comments,
    (SELECT COUNT(*) FROM follows)     AS follows,
    (SELECT COUNT(*) FROM track_likes) AS track_likes;

SELECT id, username, tier, free_tracks_left FROM users ORDER BY id;