import 'package:flutter/material.dart';

import '../app_scope.dart';
import '../content/models.dart';
import '../theme.dart';
import '../widgets/topic_tile.dart';
import 'page_screen.dart';

class LevelScreen extends StatelessWidget {
  const LevelScreen({super.key, required this.level});

  final Level level;

  static Future<void> open(BuildContext context, Level level) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => LevelScreen(level: level)));

  @override
  Widget build(BuildContext context) {
    final catalog = AppScope.of(context).catalog;
    final topics = catalog.topicsOf(level);
    final overview = [for (final id in level.pageIds) if (catalog.byId(id) case final p? when p.isIndex) p];
    final style = levelStyle(level.code);
    return Scaffold(
      appBar: AppBar(title: Text(level.code.isEmpty ? level.name : '${level.code} · ${level.name}')),
      body: ListView(
        children: [
          if (level.experience.isNotEmpty)
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
              child: Align(
                alignment: Alignment.centerLeft,
                child: Chip(avatar: Text(style.emoji), label: Text('Experience: ${level.experience}')),
              ),
            ),
          for (final p in overview)
            ListTile(
              leading: const Icon(Icons.map_outlined),
              title: const Text('Level overview'),
              subtitle: const Text('Goals, page map and the "ready for next level" checklist'),
              onTap: () => PageScreen.open(context, p),
            ),
          const Divider(),
          for (final p in topics) TopicTile(page: p),
        ],
      ),
    );
  }
}
