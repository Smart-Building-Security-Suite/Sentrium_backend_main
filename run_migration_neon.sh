#!/bin/bash
# Run migration against Neon database

# Extract connection details from .env
source .env

# Convert JDBC URL to psql format
# From: jdbc:postgresql://host/db?params
# To: postgresql://username:password@host/db?params

HOST=$(echo $DATABASE_URL | sed 's|jdbc:postgresql://||' | cut -d'/' -f1)
DB=$(echo $DATABASE_URL | sed 's|.*://[^/]*/||' | cut -d'?' -f1)
SSL_PARAM="?sslmode=require"

PSQL_URL="postgresql://${DATABASE_USERNAME}:${DATABASE_PASSWORD}@${HOST}/${DB}${SSL_PARAM}"

echo "Connecting to Neon database..."
echo "Host: $HOST"
echo "Database: $DB"
echo ""

# Check if psql is available
if ! command -v psql &> /dev/null; then
    echo "❌ psql command not found!"
    echo ""
    echo "Options to run the migration:"
    echo ""
    echo "1. Install PostgreSQL client:"
    echo "   brew install libpq"
    echo "   brew link --force libpq"
    echo ""
    echo "2. Use Neon SQL Editor (easiest):"
    echo "   - Go to https://console.neon.tech"
    echo "   - Select your project"
    echo "   - Click 'SQL Editor'"
    echo "   - Copy and paste contents of DEVICE_INTEGRATION_MIGRATION.sql"
    echo "   - Click 'Run'"
    echo ""
    echo "3. Use online SQL editor:"
    echo "   Connection string: $PSQL_URL"
    exit 1
fi

# Run the migration
echo "Running migration..."
psql "$PSQL_URL" -f DEVICE_INTEGRATION_MIGRATION.sql

if [ $? -eq 0 ]; then
    echo ""
    echo "✅ Migration completed successfully!"
    echo ""
    echo "Now restart your backend:"
    echo "  ./mvnw spring-boot:run"
else
    echo ""
    echo "❌ Migration failed. Check the error above."
fi
