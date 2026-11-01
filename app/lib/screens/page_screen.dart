import 'package:flutter/material.dart';
import 'package:markdown_widget/markdown_widget.dart';

import '../app_scope.dart';
import '../content/models.dart';
import '../widgets/code_block.dart';

class PageScreen extends StatefulWidget {
  const PageScreen({super.key, required this.page});

  final PageMeta page;

  static Future<void> open(BuildContext context, PageMeta page) =>
      Navigator.of(context).push(MaterialPageRoute(builder: (_) => PageScreen(page: page)));

  @override
  State<PageScreen> createState() => _PageScreenState();
}

class _PageScreenState extends State<PageScreen> {
  final _toc = TocController();
  late Future<String> _markdown;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final scope = AppScope.of(context);
    _markdown = scope.repository.markdown(widget.page);
    scope.progress.opened(widget.page.id);
  }

  @override
  void dispose() {
    _toc.dispose();
    super.dispose();
  }

  void _onLink(String href) {
    final catalog = AppScope.of(context).catalog;
    final target = catalog.resolveLink(widget.page, href);
    if (target != null) {
      PageScreen.open(context, target);
    } else {
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(href)));
    }
  }

  MarkdownConfig _config(BuildContext context) {
    final dark = Theme.of(context).brightness == Brightness.dark;
    final scheme = Theme.of(context).colorScheme;
    final base = dark ? MarkdownConfig.darkConfig : MarkdownConfig.defaultConfig;
    final pre = dark ? PreConfig.darkConfig : const PreConfig();
    return base.copy(configs: [
      pre.copy(wrapper: codeWrapper, language: 'java', textStyle: const TextStyle(fontSize: 13, height: 1.4, fontFamily: 'monospace', fontFamilyFallback: ['Courier New', 'Courier'])),
      LinkConfig(style: TextStyle(color: scheme.primary, decoration: TextDecoration.underline), onTap: _onLink),
    ]);
  }

  @override
  Widget build(BuildContext context) {
    final progress = AppScope.of(context).progress;
    final page = widget.page;
    return Scaffold(
      appBar: AppBar(
        title: Text(page.title, overflow: TextOverflow.ellipsis),
        actions: [
          ListenableBuilder(
            listenable: progress,
            builder: (context, _) => IconButton(
              tooltip: progress.isSaved(page.id) ? 'Remove bookmark' : 'Bookmark',
              icon: Icon(progress.isSaved(page.id) ? Icons.bookmark : Icons.bookmark_border),
              onPressed: () => progress.toggleSaved(page.id),
            ),
          ),
          Builder(
            builder: (context) => IconButton(
              tooltip: 'Contents',
              icon: const Icon(Icons.toc),
              onPressed: () => Scaffold.of(context).openEndDrawer(),
            ),
          ),
        ],
      ),
      endDrawer: Drawer(
        child: SafeArea(
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Padding(
              padding: const EdgeInsets.all(16),
              child: Text('On this page', style: Theme.of(context).textTheme.titleMedium),
            ),
            Expanded(child: TocWidget(controller: _toc)),
          ]),
        ),
      ),
      body: FutureBuilder<String>(
        future: _markdown,
        builder: (context, snapshot) {
          if (snapshot.hasError) return Center(child: Text('Could not load this page: ${snapshot.error}'));
          if (!snapshot.hasData) return const Center(child: CircularProgressIndicator());
          return MarkdownWidget(
            data: snapshot.data!,
            tocController: _toc,
            config: _config(context),
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 96),
          );
        },
      ),
      floatingActionButton: page.isIndex || !page.written
          ? null
          : ListenableBuilder(
              listenable: progress,
              builder: (context, _) {
                final read = progress.isRead(page.id);
                return FloatingActionButton.extended(
                  onPressed: () => progress.toggleRead(page.id),
                  icon: Icon(read ? Icons.check_circle : Icons.check_circle_outline),
                  label: Text(read ? 'Read' : 'Mark as read'),
                );
              },
            ),
    );
  }
}
