# Define variables
REPO="abodinagdat16/Star2D"
BRANCH="master"
TARGET_PATH="app/src/main/java"
LOCAL_PATH="./"

# Initialize counter
PROCESSED_COUNT=0

# Iterate over files in the local folder
for FILE in $(find $LOCAL_PATH -type f); do
  # Get the relative file path
  RELATIVE_PATH=${FILE#$LOCAL_PATH/}

  # Read file content
  CONTENT=$(base64 -w 0 "$FILE")

  # Check if the file exists in the repository
  API_RESPONSE=$(gh api "/repos/$REPO/contents/$TARGET_PATH/$RELATIVE_PATH" --jq '.sha' 2>/dev/null)

  # If the file exists, include the sha; otherwise, create a new file
  if [ -n "$API_RESPONSE" ]; then
    gh api \
      -X PUT \
      -H "Accept: application/vnd.github.v3+json" \
      "/repos/$REPO/contents/$TARGET_PATH/$RELATIVE_PATH" \
      -F message="Update $RELATIVE_PATH" \
      -F content="$CONTENT" \
      -F sha="$API_RESPONSE" \
      -F branch="$BRANCH" > /dev/null 2>&1
  else
    gh api \
      -X PUT \
      -H "Accept: application/vnd.github.v3+json" \
      "/repos/$REPO/contents/$TARGET_PATH/$RELATIVE_PATH" \
      -F message="Add $RELATIVE_PATH" \
      -F content="$CONTENT" \
      -F branch="$BRANCH" > /dev/null 2>&1
  fi

  # Increment counter
  ((PROCESSED_COUNT++))

  # Display file name
  echo "File: $RELATIVE_PATH"
done

# Display total processed files
echo "Processed files: $PROCESSED_COUNT"