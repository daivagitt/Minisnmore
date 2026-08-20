import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { Button } from "@/components/ui/button";

type RemoveFromCartDialogProps = {
  open: boolean;
  productName: string;
  alreadyInWishlist?: boolean;
  onOpenChange: (open: boolean) => void;
  onMoveToWishlist: () => void;
  onRemoveOnly: () => void;
};

export function RemoveFromCartDialog({
  open,
  productName,
  alreadyInWishlist = false,
  onOpenChange,
  onMoveToWishlist,
  onRemoveOnly,
}: RemoveFromCartDialogProps) {
  return (
    <AlertDialog open={open} onOpenChange={onOpenChange}>
      <AlertDialogContent>
        <AlertDialogHeader>
          <AlertDialogTitle>Remove from cart?</AlertDialogTitle>
          <AlertDialogDescription>
            {alreadyInWishlist
              ? `\u201c${productName}\u201d is already in your wishlist. Remove it from cart?`
              : `Move \u201c${productName}\u201d to your wishlist instead, or remove it from cart.`}
          </AlertDialogDescription>
        </AlertDialogHeader>
        <AlertDialogFooter>
          {alreadyInWishlist ? (
            <AlertDialogAction onClick={onRemoveOnly}>Remove from cart</AlertDialogAction>
          ) : (
            <>
              <Button type="button" variant="outline" onClick={onRemoveOnly}>
                Remove only
              </Button>
              <AlertDialogAction onClick={onMoveToWishlist}>Move to wishlist</AlertDialogAction>
            </>
          )}
        </AlertDialogFooter>
      </AlertDialogContent>
    </AlertDialog>
  );
}
