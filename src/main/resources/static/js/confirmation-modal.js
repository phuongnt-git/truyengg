/**
 * ConfirmationModal - Reusable confirmation modal component
 */
var ConfirmationModal = (function () {

    /**
     * Show confirmation modal
     * @param {Object} options - Configuration options
     * @param {string} options.title - Modal title
     * @param {string} options.message - Modal message
     * @param {string} [options.confirmText='Xác nhận'] - Confirm button text
     * @param {string} [options.cancelText='Hủy'] - Cancel button text
     * @param {boolean} [options.showReasonInput=false] - Show reason input field
     * @param {boolean} [options.showHardDeleteCheckbox=false] - Show hard delete checkbox
     * @param {Function} options.onConfirm - Callback when user confirms (receives {reason, hardDelete})
     * @param {Function} [options.onCancel] - Callback when user cancels
     */
    function show(options) {
        if (!options || !options.title || !options.message || !options.onConfirm) {
            console.error('ConfirmationModal.show() requires title, message, and onConfirm');
            return;
        }

        // Set title and message
        $('#confirmationTitle').text(options.title);
        $('#confirmationMessage').text(options.message);

        // Set button texts
        $('#confirmationConfirm').text(options.confirmText || 'Xác nhận');
        $('#confirmationCancel').text(options.cancelText || 'Hủy');

        // Show/hide reason input
        if (options.showReasonInput) {
            $('#confirmationReasonInput').show();
            $('#confirmationReason').val('');
        } else {
            $('#confirmationReasonInput').hide();
            $('#confirmationReason').val('');
        }

        // Show/hide hard delete checkbox
        if (options.showHardDeleteCheckbox) {
            $('#confirmationHardDelete').show();
            $('#hardDeleteCheckbox').prop('checked', false);
        } else {
            $('#confirmationHardDelete').hide();
            $('#hardDeleteCheckbox').prop('checked', false);
        }

        // Remove existing event handlers
        $('#confirmationConfirm').off('click');
        $('#confirmationModal').off('hidden.bs.modal');

        // Set up confirm handler
        $('#confirmationConfirm').on('click', function () {
            var reason = $('#confirmationReason').val() || null;
            var hardDelete = $('#hardDeleteCheckbox').is(':checked');

            $('#confirmationModal').modal('hide');

            // Call onConfirm callback
            if (options.onConfirm) {
                options.onConfirm({
                    reason: reason,
                    hardDelete: hardDelete
                });
            }
        });

        // Set up cancel handler
        $('#confirmationModal').on('hidden.bs.modal', function () {
            if (options.onCancel) {
                options.onCancel();
            }
        });

        // Show modal
        $('#confirmationModal').modal('show');
    }

    /**
     * Hide confirmation modal
     */
    function hide() {
        $('#confirmationModal').modal('hide');
    }

    return {
        show: show,
        hide: hide
    };
})();

