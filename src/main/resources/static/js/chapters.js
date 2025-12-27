/**
 * Chapters module for TruyenGG
 * Handles chapter reading, navigation, image lazy loading
 */

const ChaptersModule = {
    /**
     * Load chapter data
     */
    loadChapter: function (comicSlug, chapterName, containerSelector) {
        const url = `/chapters/comic/${comicSlug}/chapter/${chapterName}`;

        ApiClient.get(url, null, false).done(function (response) {
            if (response.success && response.data) {
                const chapter = response.data;
                ChaptersModule.renderChapter(chapter, containerSelector);

                // Save reading history
                if (ApiClient.getToken()) {
                    ChaptersModule.saveHistory(comicSlug, chapterName);
                }
            }
        }).fail(function (xhr) {
            console.error('Failed to load chapter:', xhr);
            showToast('Không thể tải chương', false);
        });
    },

    /**
     * Render chapter images
     */
    renderChapter: function (chapter, containerSelector) {
        const $container = $(containerSelector);
        $container.empty();

        const images = chapter.images || [];
        images.forEach(function (imageUrl, index) {
            const img = `
                <div class="chapter-image-container">
                    <img src="${imageUrl}" alt="Trang ${index + 1}" class="chapter-image lazy" data-src="${imageUrl}">
                </div>
            `;
            $container.append(img);
        });

        // Initialize lazy loading
        if (typeof $.fn.lazy !== 'undefined') {
            $container.find('.lazy').lazy({
                effect: 'fadeIn',
                effectTime: 300,
                threshold: 0
            });
        }

        // Setup keyboard navigation
        ChaptersModule.setupKeyboardNavigation(chapter);
    },

    /**
     * Setup keyboard shortcuts for navigation
     */
    setupKeyboardNavigation: function (chapter) {
        $(document).off('keydown.chapterNav');
        $(document).on('keydown.chapterNav', function (e) {
            if (e.target.tagName === 'INPUT' || e.target.tagName === 'TEXTAREA') {
                return;
            }

            if (e.key === 'ArrowLeft' && chapter.previousChapter) {
                window.location.href = `/chapter?slug=${chapter.comicSlug}&chapter=${chapter.previousChapter}`;
            } else if (e.key === 'ArrowRight' && chapter.nextChapter) {
                window.location.href = `/chapter?slug=${chapter.comicSlug}&chapter=${chapter.nextChapter}`;
            }
        });
    },

    /**
     * Save reading history
     */
    saveHistory: function (comicSlug, chapterName) {
        ApiClient.post('/history', {
            comicSlug: comicSlug,
            chapterName: chapterName
        }, true).done(function (response) {
            // History saved
        }).fail(function (xhr) {
            console.error('Failed to save history:', xhr);
        });
    },

    /**
     * Navigate to previous chapter
     */
    navigatePrevious: function (comicSlug, currentChapter) {
        // Will be implemented with chapter list
    },

    /**
     * Navigate to next chapter
     */
    navigateNext: function (comicSlug, currentChapter) {
        // Will be implemented with chapter list
    }
};
