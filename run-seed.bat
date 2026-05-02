@echo off
REM =====================================
REM DeciBel Database Seed Runner
REM =====================================
REM This script runs the seed.sql file to populate the database

echo =====================================
echo DeciBel Database Seed Script
echo =====================================
echo.

REM Prompt for database details
set /p DB_NAME="Enter database name (default: decibel_db): "
if "%DB_NAME%"=="" set DB_NAME=decibel_db

set /p DB_USER="Enter database user (default: DeciBel): "
if "%DB_USER%"=="" set DB_USER=DeciBel

set /p DB_HOST="Enter database host (default: localhost): "
if "%DB_HOST%"=="" set DB_HOST=localhost

set /p DB_PORT="Enter database port (default: 5432): "
if "%DB_PORT%"=="" set DB_PORT=5432

echo.
echo Running seed script...
echo Database: %DB_NAME%
echo User: %DB_USER%
echo Host: %DB_HOST%
echo Port: %DB_PORT%
echo.

REM Run the seed script
psql -h %DB_HOST% -p %DB_PORT% -U %DB_USER% -d %DB_NAME% -f seed.sql

if %ERRORLEVEL% EQU 0 (
    echo.
    echo =====================================
    echo Seed script completed successfully!
    echo =====================================
) else (
    echo.
    echo =====================================
    echo ERROR: Seed script failed!
    echo =====================================
)

echo.
pause
