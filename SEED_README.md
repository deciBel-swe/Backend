# DeciBel Database Seed Script

This seed script populates your DeciBel database with sample data for development and testing.

## What's Included

The seed script creates:

### Users (5)
- **djmike** - Electronic music producer (FREE tier)
- **sarahbeats** - Lo-fi beats artist (PRO tier)
- **rocknroll99** - Rock cover artist (FREE tier)
- **jazzlover** - Jazz musician (PRO tier)
- **indieartist** - Indie artist (FREE tier, private profile)

**Login credentials for all users:**
- Password: `Password123!`
- Emails: `djmike@example.com`, `sarah@example.com`, etc.

### Admin (1)
- Username: `admin`
- Email: `admin@decibel.com`
- Password: `Admin123!`

### Tracks (10)
- 9 published tracks across different genres
- 1 work-in-progress track
- Complete metadata including play counts, likes, reposts

### Playlists (4)
- Mix of PLAYLIST and ALBUM types
- With tracks, likes, and reposts

### Social Features
- Follows (10 relationships)
- Blocks (1 blocked user)
- Comments (8 comments with replies)
- Notifications (6 notifications)
- Track likes (12)
- Track reposts (6)
- Playlist likes (5)
- Playlist reposts (3)

### Additional Data
- Tags (10)
- Social links
- Notification preferences
- Subscriptions (2 PRO users)
- Reports (2)
- Sharing tokens

## How to Run

### Option 1: Using the Batch Script (Windows)

1. Double-click `run-seed.bat`
2. Enter your database details when prompted (or press Enter for defaults)
3. Wait for completion

### Option 2: Using Command Line

```bash
# Make sure PostgreSQL is running
psql -U DeciBel -d your_database_name -f seed.sql
```

Replace:
- `DeciBel` with your database username
- `your_database_name` with your database name

### Option 3: Using pgAdmin or DBeaver

1. Open your database tool
2. Connect to your database
3. Open the `seed.sql` file
4. Execute the script

## Important Notes

⚠️ **Warning**: This script will **DELETE ALL EXISTING DATA** in your database before seeding!

The script:
1. Truncates all tables
2. Resets all sequences
3. Inserts fresh seed data
4. Resets sequence counters

## What Gets Created

### Users & Authentication
- 5 users with verified email addresses
- 1 admin account
- BCrypt password hashes
- Notification preferences for each user

### Content
- 10 tracks (9 public, 1 private)
- 4 playlists
- 10 tags
- Track-tag relationships
- Playlist genres

### Engagement
- Social follows
- Likes and reposts
- Comments with replies
- Notifications

### Monetization
- 2 active PRO subscriptions
- Stripe customer IDs (test data)

### Moderation
- 1 active block
- 2 reports (1 resolved, 1 in review)

## Testing the Data

After running the seed script, you can:

1. **Login as a user:**
   - Email: `djmike@example.com`
   - Password: `Password123!`

2. **Login as admin:**
   - Email: `admin@decibel.com`
   - Password: `Admin123!`

3. **Browse tracks:**
   - User `sarahbeats` has the most tracks (4)
   - Track "Lo-fi Study Session" has the most plays (5600)

4. **Test social features:**
   - Users already follow each other
   - Tracks have likes and comments
   - Playlists are populated

## Customization

To modify the seed data:

1. Edit `seed.sql`
2. Adjust the INSERT statements
3. Run the script again

## Troubleshooting

### Error: "permission denied"
- Make sure you're using the correct database user
- Ensure the user has write permissions

### Error: "database does not exist"
- Create the database first: `createdb -U DeciBel your_database_name`
- Or use the database name from your `application.properties`

### Error: "psql: command not found"
- Install PostgreSQL client tools
- Add PostgreSQL bin directory to PATH
- Or use pgAdmin/DBeaver instead

### Error: "duplicate key value violates unique constraint"
- The script includes TRUNCATE to clear existing data
- If you still get this error, try dropping and recreating the database

## Verify Installation

After running the seed script, you should see:

```
Seed data inserted successfully!

 users | tracks | playlists | comments | follows | likes
-------+--------+-----------+----------+---------+-------
     5 |     10 |         4 |        8 |      10 |    12
```

## Next Steps

1. Start your Spring Boot application
2. Login with any of the seeded users
3. Explore the pre-populated data
4. Test features with realistic data

## License

This seed data is for development and testing purposes only.
