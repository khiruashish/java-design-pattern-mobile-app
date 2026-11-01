import 'package:flutter/material.dart';

import 'app_scope.dart';
import 'content/content_repository.dart';
import 'progress/progress_store.dart';
import 'screens/home_screen.dart';
import 'theme.dart';

/// Composition root: the only place that picks concrete implementations.
Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final repository = AssetContentRepository();
  final catalog = await repository.catalog();
  final progress = await ProgressStore.load();
  runApp(AppScope(
    repository: repository,
    catalog: catalog,
    progress: progress,
    child: const JavaPatternsApp(),
  ));
}

class JavaPatternsApp extends StatelessWidget {
  const JavaPatternsApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Java Design Patterns',
      debugShowCheckedModeBanner: false,
      theme: buildTheme(Brightness.light),
      darkTheme: buildTheme(Brightness.dark),
      home: const HomeScreen(),
    );
  }
}
