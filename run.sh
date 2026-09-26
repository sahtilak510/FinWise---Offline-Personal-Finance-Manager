#!/bin/bash

# Offline Personal Finance Manager - Start Script

echo "=========================================="
echo "Starting Offline Personal Finance Manager"
echo "=========================================="
echo ""

# Check if Java is installed
if ! command -v java &> /dev/null; then
    echo "❌ Error: Java is not installed!"
    echo "Please install Java 17 or higher."
    exit 1
fi

JAVA_VERSION=$(java -version 2>&1 | awk -F '"' '/version/ {print $2}' | cut -d'.' -f1)
echo "✓ Java version detected: $(java -version 2>&1 | head -1)"

# Check if Maven is installed
if ! command -v mvn &> /dev/null; then
    echo "❌ Error: Maven is not installed!"
    echo "Please install Maven 3.6 or higher."
    exit 1
fi

echo "✓ Maven version: $(mvn -version | head -1)"
echo ""

# Create necessary directories
mkdir -p data logs

echo "📁 Creating necessary directories..."
echo "✓ Created: data/ (for database)"
echo "✓ Created: logs/ (for application logs)"
echo ""

# Build the project
echo "🔨 Building the project..."
mvn clean install -DskipTests

if [ $? -ne 0 ]; then
    echo "❌ Build failed!"
    exit 1
fi

echo ""
echo "✓ Build successful!"
echo ""

# Run the application
echo "🚀 Starting the application..."
echo "📱 Access the application at: http://localhost:8080"
echo "👤 Default credentials:"
echo "   Username: admin"
echo "   Password: admin123"
echo ""
echo "Press Ctrl+C to stop the application"
echo ""

java -jar target/offline-finance-manager-1.0.0.jar
