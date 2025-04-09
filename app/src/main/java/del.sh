REPO="star4droid/Star2D"
BRANCH="master"
TARGET_PATH="app/build"

# Function to recursively delete files and folders
delete_folder() {
  local path=$1

  # Get the contents of the folder
  CONTENTS=$(gh api -H "Accept: application/vnd.github.v3+json" \
    /repos/$REPO/contents/$path?ref=$BRANCH)

  # Check if the folder contains files or subdirectories
  for ITEM in $(echo "$CONTENTS" | jq -c '.[]'); do
    ITEM_PATH=$(echo "$ITEM" | jq -r '.path')
    ITEM_TYPE=$(echo "$ITEM" | jq -r '.type')

    if [ "$ITEM_TYPE" = "file" ]; then
      # Delete the file
      echo "Deleting file: $ITEM_PATH..."
      SHA=$(echo "$ITEM" | jq -r '.sha')
      gh api -X DELETE -H "Accept: application/vnd.github.v3+json" \
        /repos/$REPO/contents/$ITEM_PATH \
        -f message="Delete $ITEM_PATH" \
        -f sha="$SHA" \
        -f branch="$BRANCH"
    elif [ "$ITEM_TYPE" = "dir" ]; then
      # Recursively delete the folder
      echo "Deleting folder: $ITEM_PATH..."
      delete_folder "$ITEM_PATH"
    fi
  done

  # After deleting all contents, remove the folder
  echo "Removing folder: $path..."
  SHA=$(gh api -H "Accept: application/vnd.github.v3+json" \
    /repos/$REPO/contents/$path?ref=$BRANCH | jq -r '.sha' 2>/dev/null)
  if [ -n "$SHA" ]; then
    gh api -X DELETE -H "Accept: application/vnd.github.v3+json" \
      /repos/$REPO/contents/$path \
      -f message="Delete folder $path" \
      -f sha="$SHA" \
      -f branch="$BRANCH"
  fi
}

# Start deleting the target folder
delete_folder "$TARGET_PATH"
