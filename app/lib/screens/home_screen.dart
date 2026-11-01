import 'package:flutter/material.dart';

import '../app_scope.dart';
import '../content/models.dart';
import '../theme.dart';
import '../widgets/topic_tile.dart';
import 'level_screen.dart';
import 'page_screen.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  int _tab = 0;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: IndexedStack(index: _tab, children: const [_LearnTab(), _SearchTab(), _SavedTab()]),
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _tab,
        onDestinationSelected: (i) => setState(() => _tab = i),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.school_outlined), selectedIcon: Icon(Icons.school), label: 'Learn'),
          NavigationDestination(icon: Icon(Icons.search), label: 'Search'),
          NavigationDestination(icon: Icon(Icons.bookmarks_outlined), selectedIcon: Icon(Icons.bookmarks), label: 'Saved'),
        ],
      ),
    );
  }
}

class _LearnTab extends StatelessWidget {
  const _LearnTab();

  @override
  Widget build(BuildContext context) {
    final scope = AppScope.of(context);
    final catalog = scope.catalog;
    final text = Theme.of(context).textTheme;
    final scheme = Theme.of(context).colorScheme;
    final topics = catalog.allTopics;
    final written = topics.where((p) => p.written).length;

    return ListenableBuilder(
      listenable: scope.progress,
      builder: (context, _) {
        final read = scope.progress.readCount(topics.map((p) => p.id));
        final last = scope.progress.lastOpened == null ? null : catalog.byId(scope.progress.lastOpened!);
        return CustomScrollView(
          slivers: [
            SliverToBoxAdapter(
              child: Container(
                margin: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                padding: const EdgeInsets.all(20),
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(20),
                  gradient: LinearGradient(colors: [scheme.primary, scheme.tertiary]),
                ),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text('Java Design Patterns', style: text.headlineSmall?.copyWith(color: scheme.onPrimary, fontWeight: FontWeight.w700)),
                    const SizedBox(height: 4),
                    Text('From Day 0 to principal engineer, with runnable Java 21 examples.',
                        style: text.bodyMedium?.copyWith(color: scheme.onPrimary.withValues(alpha: 0.9))),
                    const SizedBox(height: 16),
                    LinearProgressIndicator(
                      value: topics.isEmpty ? 0 : read / topics.length,
                      backgroundColor: scheme.onPrimary.withValues(alpha: 0.25),
                      color: scheme.onPrimary,
                      borderRadius: BorderRadius.circular(4),
                    ),
                    const SizedBox(height: 6),
                    Text('$read of ${topics.length} topics read · $written available now',
                        style: text.labelMedium?.copyWith(color: scheme.onPrimary)),
                  ],
                ),
              ),
            ),
            if (last != null)
              SliverToBoxAdapter(
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
                  child: Card.filled(
                    child: ListTile(
                      leading: const Icon(Icons.play_circle_outline),
                      title: const Text('Continue reading'),
                      subtitle: Text(last.title),
                      onTap: () => PageScreen.open(context, last),
                    ),
                  ),
                ),
              ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
              sliver: SliverToBoxAdapter(child: Text('Levels', style: text.titleMedium)),
            ),
            SliverList.builder(
              itemCount: catalog.levels.length,
              itemBuilder: (context, i) => _LevelCard(level: catalog.levels[i]),
            ),
            SliverPadding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
              sliver: SliverToBoxAdapter(child: Text('More', style: text.titleMedium)),
            ),
            SliverList.list(children: [
              for (final section in catalog.sections)
                ListTile(
                  leading: Icon(switch (section.id) {
                    'reference' => Icons.menu_book_outlined,
                    'projects' => Icons.construction_outlined,
                    _ => Icons.record_voice_over_outlined,
                  }),
                  title: Text(section.name),
                  trailing: const Icon(Icons.chevron_right),
                  onTap: () => LevelScreen.open(context, section),
                ),
              const SizedBox(height: 24),
            ]),
          ],
        );
      },
    );
  }
}

class _LevelCard extends StatelessWidget {
  const _LevelCard({required this.level});

  final Level level;

  @override
  Widget build(BuildContext context) {
    final scope = AppScope.of(context);
    final topics = scope.catalog.topicsOf(level);
    final read = scope.progress.readCount(topics.map((p) => p.id));
    final style = levelStyle(level.code);
    final text = Theme.of(context).textTheme;
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 4),
      child: Card.outlined(
        child: InkWell(
          onTap: () => LevelScreen.open(context, level),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                CircleAvatar(
                  backgroundColor: style.color.withValues(alpha: 0.15),
                  child: Text(level.code, style: TextStyle(color: style.color, fontWeight: FontWeight.w700)),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('${style.emoji} ${level.name}', style: text.titleMedium),
                      Text('${level.experience} · ${topics.length} topics', style: text.bodySmall),
                      const SizedBox(height: 8),
                      LinearProgressIndicator(
                        value: topics.isEmpty ? 0 : read / topics.length,
                        color: style.color,
                        borderRadius: BorderRadius.circular(4),
                      ),
                    ],
                  ),
                ),
                const SizedBox(width: 8),
                const Icon(Icons.chevron_right),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _SearchTab extends StatefulWidget {
  const _SearchTab();

  @override
  State<_SearchTab> createState() => _SearchTabState();
}

class _SearchTabState extends State<_SearchTab> {
  String _query = '';

  @override
  Widget build(BuildContext context) {
    final catalog = AppScope.of(context).catalog;
    final q = _query.trim().toLowerCase();
    final results = q.isEmpty
        ? const <PageMeta>[]
        : catalog.pages
            .where((p) => !p.isIndex && (p.title.toLowerCase().contains(q) || p.summary.toLowerCase().contains(q) || p.tags.any((t) => t.toLowerCase() == q)))
            .toList();
    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.all(16),
          child: SearchBar(
            hintText: 'Search patterns, e.g. strategy, SOLID, proxy',
            leading: const Icon(Icons.search),
            onChanged: (v) => setState(() => _query = v),
          ),
        ),
        Expanded(
          child: q.isEmpty
              ? const _Hint(icon: Icons.manage_search, text: 'Type a pattern, principle or tag such as "creational".')
              : results.isEmpty
                  ? _Hint(icon: Icons.search_off, text: 'Nothing matches "$_query".')
                  : ListView(children: [for (final p in results) TopicTile(page: p, showGroup: true)]),
        ),
      ],
    );
  }
}

class _SavedTab extends StatelessWidget {
  const _SavedTab();

  @override
  Widget build(BuildContext context) {
    final scope = AppScope.of(context);
    return ListenableBuilder(
      listenable: scope.progress,
      builder: (context, _) {
        final saved = [for (final p in scope.catalog.pages) if (scope.progress.isSaved(p.id)) p];
        return Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 20, 16, 8),
              child: Text('Saved pages', style: Theme.of(context).textTheme.titleLarge),
            ),
            Expanded(
              child: saved.isEmpty
                  ? const _Hint(icon: Icons.bookmark_border, text: 'Tap the bookmark on any page to save it here.')
                  : ListView(children: [for (final p in saved) TopicTile(page: p, showGroup: true)]),
            ),
          ],
        );
      },
    );
  }
}

class _Hint extends StatelessWidget {
  const _Hint({required this.icon, required this.text});

  final IconData icon;
  final String text;

  @override
  Widget build(BuildContext context) => Center(
        child: Padding(
          padding: const EdgeInsets.all(32),
          child: Column(mainAxisSize: MainAxisSize.min, children: [
            Icon(icon, size: 48, color: Theme.of(context).colorScheme.outline),
            const SizedBox(height: 12),
            Text(text, textAlign: TextAlign.center),
          ]),
        ),
      );
}
