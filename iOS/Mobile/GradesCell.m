//
//  GradesCell.m
//  Mobile
//
//  Created by Jason Hocker on 8/2/12.
//  Copyright (c) 2012 Ellucian. All rights reserved.
//

#import "GradesCell.h"

@implementation GradesCell
@synthesize gradeTypeLabel;
@synthesize gradeValueLabel;

- (id)initWithStyle:(UITableViewCellStyle)style reuseIdentifier:(NSString *)reuseIdentifier
{
    self = [super initWithStyle:style reuseIdentifier:reuseIdentifier];
    if (self) {
        // Initialization code
    }
    return self;
}

- (void)setSelected:(BOOL)selected animated:(BOOL)animated
{
    [super setSelected:selected animated:animated];

    // Configure the view for the selected state
}

@end
