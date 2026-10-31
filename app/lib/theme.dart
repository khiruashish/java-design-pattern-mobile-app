import 'package:flutter/material.dart';

const _seed = Colors.indigo;

ThemeData buildTheme(Brightness brightness) {
  final scheme = ColorScheme.fromSeed(seedColor: _seed, brightness: brightness);
  return ThemeData(
    colorScheme: scheme,
    useMaterial3: true,
    cardTheme: const CardThemeData(clipBehavior: Clip.antiAlias),
    appBarTheme: AppBarTheme(backgroundColor: scheme.surface, scrolledUnderElevation: 1),
  );
}

/// Level colours and emoji match the website's badges.
({Color color, String emoji}) levelStyle(String code) => switch (code) {
      'L0' => (color: Colors.blueGrey, emoji: '⚪'),
      'L1' => (color: Colors.green, emoji: '🟢'),
      'L2' => (color: Colors.blue, emoji: '🔵'),
      'L3' => (color: Colors.amber.shade700, emoji: '🟡'),
      'L4' => (color: Colors.deepOrange, emoji: '🟠'),
      'L5' => (color: Colors.red, emoji: '🔴'),
      _ => (color: Colors.indigo, emoji: '📘'),
    };
