#!/bin/bash

# Note to semitone offset (C = 0)
declare -A NOTES=(
  ["C"]=0 ["C#"]=1 ["D"]=2 ["D#"]=3 ["E"]=4 ["F"]=5
  ["F#"]=6 ["G"]=7 ["G#"]=8 ["A"]=9 ["A#"]=10 ["B"]=11
  ["Db"]=1 ["Eb"]=3 ["Gb"]=6 ["Ab"]=8 ["Bb"]=10
)

shopt -s nocaseglob
for file in *.mp3; do
  base=$(basename "$file" .mp3)

  # Extract note and octave
  if [[ "$base" =~ ^([A-Ga-g][#b]?)([0-8])$ ]]; then
    note="${BASH_REMATCH[1]^}"     # Normalize to capital
    octave="${BASH_REMATCH[2]}"

    semitone=${NOTES[$note]}
    if [[ -z "$semitone" ]]; then
      echo "⚠️ Skipping $file: Unrecognized note $note"
      continue
    fi

    midi=$(( (octave + 1) * 12 + semitone ))
    new="piano_${midi}.mp3"
    echo "🎵 $file -> $new"
    mv "$file" "$new"
  else
    echo "⚠️ Skipping $file: Format not recognized"
  fi
done
